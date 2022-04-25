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

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reference counted fs handling.
 *
 * <p>The implementation closes file systems opened by itself once they are closed as often as they were opened. This
 * allows intersecting open+close actions on e.g. the zip based file systems. The caller has to ensure open and close
 * invocations are mirrored.
 */
public final class FileSystemHandler {
	public static FileSystem open(URI uri) throws IOException {
		synchronized (fsRefs) {
			boolean opened = false;
			FileSystem ret = null;

			try {
				ret = FileSystems.getFileSystem(uri);
			} catch (FileSystemNotFoundException e) {
				try {
					ret = FileSystems.newFileSystem(uri, Collections.emptyMap());
					opened = true;
				} catch (FileSystemAlreadyExistsException f) {
					ret = FileSystems.getFileSystem(uri);
				}
			}

			Integer count = fsRefs.get(ret);

			if (count == null || count == 0) {
				count = opened ? 1 : -1;
			} else if (opened) {
				throw new IllegalStateException("fs ref tracking indicates fs "+ret+" is open, but it wasn't");
			} else {
				count += Integer.signum(count);
			}

			fsRefs.put(ret, count);

			return ret;
		}
	}

	public static void close(FileSystem fs) throws IOException {
		synchronized (fsRefs) {
			Integer count = fsRefs.get(fs);
			if (count == null || count == 0) throw new IllegalStateException("fs "+fs+" never opened via FileSystemHandler");

			boolean canClose = count > 0;
			count -= Integer.signum(count);

			if (count == 0) {
				fsRefs.remove(fs);
				if (canClose) fs.close();
			} else {
				fsRefs.put(fs, count);
			}
		}
	}

	// fs->refCount map, counting positive if the fs was originally opened by this system
	// public for reflection only!
	public static final Map<FileSystem, Integer> fsRefs;

	static {
		synchronized (FileSystems.class) {
			final String property = "fsRefsProvider";
			String provider = System.getProperty(property);

			if (provider != null) {
				try {
					int pos = provider.lastIndexOf('/');
					@SuppressWarnings("unchecked")
					Map<FileSystem, Integer> map = (Map<FileSystem, Integer>) Class.forName(provider.substring(0, pos)).getField(provider.substring(pos + 1)).get(null);
					fsRefs = map;
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			} else {
				fsRefs = new IdentityHashMap<>();
				provider = String.format("%s/%s", FileSystemHandler.class.getName(), "fsRefs");
				System.setProperty(property, provider);
			}
		}
	}
}
