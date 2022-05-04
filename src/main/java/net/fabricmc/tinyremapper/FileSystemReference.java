/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2019, 2022, FabricMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reference counted fs handling.
 *
 * <p>The implementation closes file systems opened by itself once they are closed as often as they were opened. This
 * allows intersecting open+close actions on e.g. the zip based file systems. The caller has to ensure open and close
 * invocations are mirrored.
 */
public final class FileSystemReference implements Closeable {
	public static FileSystemReference openJar(Path path) throws IOException {
		return openJar(path, false);
	}

	public static FileSystemReference openJar(Path path, boolean create) throws IOException {
		return open(toJarUri(path), create);
	}

	private static URI toJarUri(Path path) {
		URI uri = path.toUri();

		try {
			return new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath(), uri.getFragment());
		} catch (URISyntaxException e) {
			throw new RuntimeException("can't convert path "+path+" to uri", e);
		}
	}

	public static FileSystemReference open(URI uri) throws IOException {
		return open(uri, false);
	}

	public static FileSystemReference open(URI uri, boolean create) throws IOException {
		synchronized (openFsMap) {
			boolean opened = false;
			FileSystem fs = null;

			try {
				fs = FileSystems.getFileSystem(uri);
			} catch (FileSystemNotFoundException e) {
				try {
					fs = FileSystems.newFileSystem(uri, create ? Collections.singletonMap("create", "true") : Collections.emptyMap());
					opened = true;
				} catch (FileSystemAlreadyExistsException f) {
					fs = FileSystems.getFileSystem(uri);
				}
			}

			FileSystemReference ret = new FileSystemReference(fs);
			Set<FileSystemReference> refs = openFsMap.get(fs);

			if (refs == null) {
				refs = Collections.newSetFromMap(new IdentityHashMap<>());
				openFsMap.put(fs, refs);
				if (!opened) refs.add(null);
			} else if (opened) {
				throw new IllegalStateException("opened but already in refs?");
			}

			refs.add(ret);

			return ret;
		}
	}

	private FileSystemReference(FileSystem fs) {
		this.fileSystem = fs;
	}

	public boolean isReadOnly() {
		if (closed) throw new IllegalStateException("fs closed");

		return fileSystem.isReadOnly();
	}

	public Path getPath(String first, String... more) {
		if (closed) throw new IllegalStateException("fs closed");

		return fileSystem.getPath(first, more);
	}

	public FileSystem getFs() {
		if (closed) throw new IllegalStateException("fs closed");

		return fileSystem;
	}

	@Override
	public void close() throws IOException {
		synchronized (openFsMap) {
			if (closed) return;
			closed = true;

			Set<FileSystemReference> refs = openFsMap.get(fileSystem);
			if (refs == null || !refs.remove(this)) throw new IllegalStateException("fs "+fileSystem+" was already closed");

			if (refs.isEmpty()) {
				openFsMap.remove(fileSystem);
				fileSystem.close();
			} else if (refs.size() == 1 && refs.contains(null)) { // only null -> not opened by us, just abandon
				openFsMap.remove(fileSystem);
			}
		}
	}

	@Override
	public String toString() {
		synchronized (openFsMap) {
			Set<FileSystemReference> refs = openFsMap.getOrDefault(fileSystem, Collections.emptySet());
			return String.format("%s=%dx,%s", fileSystem, refs.size(), refs.contains(null) ? "existing" : "new");
		}
	}

	private static final Map<FileSystem, Set<FileSystemReference>> openFsMap = new IdentityHashMap<>();

	private final FileSystem fileSystem;
	private volatile boolean closed;
}
