/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2016, 2021, FabricMC
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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class OutputConsumerPath implements BiConsumer<String, byte[]>, Closeable {
	public static class Builder {
		public Builder(Path destination) {
			this.destination = destination;
		}

		public Builder assumeArchive(boolean value) {
			this.assumeArchive = value;
			return this;
		}

		public Builder keepFsOpen(boolean value) {
			this.keepFsOpen = value;
			return this;
		}

		public Builder threadSyncWrites(boolean value) {
			this.threadSyncWrites = value;
			return this;
		}

		public Builder filter(Predicate<String> classNameFilter) {
			this.classNameFilter = classNameFilter;
			return this;
		}

		public OutputConsumerPath build() throws IOException {
			boolean isJar = assumeArchive == null || Files.exists(destination) ? isJar(destination) : assumeArchive;

			return new OutputConsumerPath(destination, isJar, keepFsOpen, threadSyncWrites, classNameFilter);
		}

		private final Path destination;
		private Boolean assumeArchive;
		private boolean keepFsOpen = false;
		private boolean threadSyncWrites = false;
		private Predicate<String> classNameFilter;
	}

	@Deprecated
	public OutputConsumerPath(Path dstFile) throws IOException {
		this(dstFile, true);
	}

	@Deprecated
	public OutputConsumerPath(Path dstDir, boolean closeFs) throws IOException {
		this(dstDir, isJar(dstDir), !closeFs, false, null);
	}

	private OutputConsumerPath(Path destination, boolean isJar, boolean keepFsOpen, boolean threadSyncWrites,
			Predicate<String> classNameFilter) throws IOException {
		if (!isJar) { // TODO: implement .class output (for processing a single class file)
			Files.createDirectories(destination);
		} else {
			createParentDirs(destination);
			URI uri;

			try {
				uri = new URI("jar:"+destination.toUri().toString());
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}

			Map<String, String> env = new HashMap<>();
			env.put("create", "true");

			FileSystem fs = FileSystems.newFileSystem(uri, env);
			if (fs.isReadOnly()) throw new IOException("the jar file "+destination+" can't be written");

			destination = fs.getPath("/");
		}

		this.dstDir = destination;
		this.closeFs = isJar && !keepFsOpen;
		this.isJarFs = isJar;
		this.lock = threadSyncWrites ? new ReentrantLock() : null;
		this.classNameFilter = classNameFilter;
	}

	public void addNonClassFiles(Path srcFile) throws IOException {
		addNonClassFiles(srcFile, NonClassCopyMode.UNCHANGED, null);
	}

	public void addNonClassFiles(Path srcDir, boolean closeFs) throws IOException {
		addNonClassFiles(srcDir, NonClassCopyMode.UNCHANGED, null, closeFs);
	}

	public void addNonClassFiles(Path srcFile, NonClassCopyMode copyMode, TinyRemapper remapper) throws IOException {
		this.addNonClassFiles(srcFile, remapper, copyMode.remappers);
	}

	public void addNonClassFiles(Path srcFile, TinyRemapper remapper, List<ResourceRemapper> remappers) throws IOException {
		if (Files.isDirectory(srcFile)) {
			addNonClassFiles(srcFile, remapper, false, remappers);
		} else if (Files.exists(srcFile)) {
			if (!srcFile.getFileName().toString().endsWith(classSuffix)) {
				addNonClassFiles(FileSystems.newFileSystem(srcFile, (ClassLoader) null).getPath("/"), remapper, true, remappers);
			}
		} else {
			throw new FileNotFoundException("file "+srcFile+" doesn't exist");
		}
	}

	public void addNonClassFiles(Path srcDir, NonClassCopyMode copyMode, TinyRemapper remapper, boolean closeFs) throws IOException {
		this.addNonClassFiles(srcDir, remapper, closeFs, copyMode.remappers);
	}

	public void addNonClassFiles(Path srcDir, TinyRemapper remapper, boolean closeFs, List<ResourceRemapper> resourceRemappers) throws IOException {
		try {
			if (lock != null) lock.lock();
			if (closed) throw new IllegalStateException("consumer already closed");

			Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					String fileName = file.getFileName().toString();

					if (!fileName.endsWith(classSuffix)) {
						Path relativePath = srcDir.relativize(file);
						Path dstFile = dstDir.resolve(relativePath.toString()); // toString bypasses resolve requiring identical fs providers

						for (ResourceRemapper resourceRemapper : resourceRemappers) {
							if (resourceRemapper.canTransform(remapper, relativePath)) {
								try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
									resourceRemapper.transform(dstDir, relativePath, input, remapper);
									return FileVisitResult.CONTINUE;
								}
							}
						}

						createParentDirs(dstFile);
						Files.copy(file, dstFile, StandardCopyOption.REPLACE_EXISTING);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} finally {
			if (lock != null) lock.unlock();

			if (closeFs) srcDir.getFileSystem().close();
		}
	}

	@Override
	public void accept(String clsName, byte[] data) {
		if (classNameFilter != null && !classNameFilter.test(clsName)) return;

		Path dstFile = null;

		try {
			if (lock != null) lock.lock();
			if (closed) throw new IllegalStateException("consumer already closed");

			dstFile = dstDir.resolve(clsName + classSuffix);

			if (isJarFs && Files.exists(dstFile)) {
				if (Files.isDirectory(dstFile)) throw new FileAlreadyExistsException("dst file "+dstFile+" is a directory");

				Files.delete(dstFile); // workaround for sporadic FileAlreadyExistsException (Files.write should overwrite, jdk bug?)
			}

			createParentDirs(dstFile);
			Files.write(dstFile, data);
		} catch (IOException e) {
			throw new UncheckedIOException("error writing to "+dstFile, e);
		} finally {
			if (lock != null) lock.unlock();
		}
	}

	@Override
	public void close() throws IOException {
		if (closed) return;

		try {
			if (lock != null) lock.lock();

			if (closeFs) {
				dstDir.getFileSystem().close();
			}

			closed = true;
		} finally {
			if (lock != null) lock.unlock();
		}
	}

	private static boolean isJar(Path path) {
		if (Files.exists(path)) {
			return !Files.isDirectory(path);
		}

		String name = path.getFileName().toString().toLowerCase(Locale.ENGLISH);

		return name.endsWith(".jar") || name.endsWith(".zip");
	}

	private static void createParentDirs(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent != null) Files.createDirectories(parent);
	}

	private static final String classSuffix = ".class";

	private final Path dstDir;
	private final boolean closeFs;
	private final boolean isJarFs;
	private final Lock lock;
	private final Predicate<String> classNameFilter;
	private boolean closed;

	public interface ResourceRemapper {
		boolean canTransform(TinyRemapper remapper, Path relativePath);

		void transform(Path destinationDirectory, Path relativePath, InputStream input, TinyRemapper remapper) throws IOException;
	}
}
