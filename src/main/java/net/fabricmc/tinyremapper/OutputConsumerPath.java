/*
 * Copyright (C) 2016, 2018 Player, asie
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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

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
		if (Files.isDirectory(srcFile)) {
			addNonClassFiles(srcFile, copyMode, remapper, false);
		} else if (Files.exists(srcFile)) {
			if (!srcFile.getFileName().toString().endsWith(classSuffix)) {
				addNonClassFiles(FileSystems.newFileSystem(srcFile, null).getPath("/"), copyMode, remapper, true);
			}
		} else {
			throw new FileNotFoundException("file "+srcFile+" doesn't exist");
		}
	}

	public void addNonClassFiles(Path srcDir, NonClassCopyMode copyMode, TinyRemapper remapper, boolean closeFs) throws IOException {
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

						if (copyMode == NonClassCopyMode.UNCHANGED
								|| !relativePath.startsWith("META-INF")
								|| copyMode == NonClassCopyMode.SKIP_META_INF && relativePath.getNameCount() != 2) { // allow sub-folders of META-INF
							createParentDirs(dstFile);
							Files.copy(file, dstFile, StandardCopyOption.REPLACE_EXISTING);
						} else if (copyMode == NonClassCopyMode.FIX_META_INF && !shouldStripForFixMeta(relativePath)) {
							createParentDirs(dstFile);

							if (fileName.equals("MANIFEST.MF")) {
								Manifest manifest;

								try (InputStream is = Files.newInputStream(file)) {
									manifest = new Manifest(is);
								}

								fixManifest(manifest, remapper);

								try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(dstFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
									manifest.write(os);
								}
							} else if (remapper != null && relativePath.getNameCount() == 3 && relativePath.getName(1).toString().equals("services")) {
								fileName = mapFullyQualifiedClassName(fileName, remapper);

								try (BufferedReader reader = Files.newBufferedReader(file);
										BufferedWriter writer = Files.newBufferedWriter(dstFile.getParent().resolve(fileName), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
									fixServiceDecl(reader, writer, remapper);
								}
							} else {
								Files.copy(file, dstFile, StandardCopyOption.REPLACE_EXISTING);
							}
						}
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} finally {
			if (lock != null) lock.unlock();

			if (closeFs) srcDir.getFileSystem().close();
		}
	}

	private static boolean shouldStripForFixMeta(Path file) {
		if (file.getNameCount() != 2) return false; // not directly inside META-INF dir

		assert file.getName(0).toString().equals("META-INF");

		String fileName = file.getFileName().toString();

		// https://docs.oracle.com/en/java/javase/12/docs/specs/jar/jar.html#signed-jar-file
		return fileName.endsWith(".SF")
				|| fileName.endsWith(".DSA")
				|| fileName.endsWith(".RSA")
				|| fileName.startsWith("SIG-");
	}

	private static String mapFullyQualifiedClassName(String name, TinyRemapper remapper) {
		assert name.indexOf('/') < 0;

		return remapper.mapClass(name.replace('.', '/')).replace('/', '.');
	}

	private static void fixManifest(Manifest manifest, TinyRemapper remapper) {
		Attributes mainAttrs = manifest.getMainAttributes();

		if (remapper != null) {
			String val = mainAttrs.getValue(Attributes.Name.MAIN_CLASS);
			if (val != null) mainAttrs.put(Attributes.Name.MAIN_CLASS, mapFullyQualifiedClassName(val, remapper));

			val = mainAttrs.getValue("Launcher-Agent-Class");
			if (val != null) mainAttrs.put("Launcher-Agent-Class", mapFullyQualifiedClassName(val, remapper));
		}

		mainAttrs.remove(Attributes.Name.SIGNATURE_VERSION);

		for (Iterator<Attributes> it = manifest.getEntries().values().iterator(); it.hasNext(); ) {
			Attributes attrs = it.next();

			for (Iterator<Object> it2 = attrs.keySet().iterator(); it2.hasNext(); ) {
				Attributes.Name attrName = (Attributes.Name) it2.next();
				String name = attrName.toString();

				if (name.endsWith("-Digest") || name.contains("-Digest-") || name.equals("Magic")) {
					it2.remove();
				}
			}

			if (attrs.isEmpty()) it.remove();
		}
	}

	private static void fixServiceDecl(BufferedReader reader, BufferedWriter writer, TinyRemapper remapper) throws IOException {
		String line;

		while ((line = reader.readLine()) != null) {
			int end = line.indexOf('#');
			if (end < 0) end = line.length();

			// trim start+end to skip ' ' and '\t'

			int start = 0;
			char c;

			while (start < end && ((c = line.charAt(start)) == ' ' || c == '\t')) {
				start++;
			}

			while (end > start && ((c = line.charAt(end - 1)) == ' ' || c == '\t')) {
				end--;
			}

			if (start == end) {
				writer.write(line);
			} else {
				writer.write(line, 0, start);
				writer.write(mapFullyQualifiedClassName(line.substring(start, end), remapper));
				writer.write(line, end, line.length() - end);
			}

			writer.newLine();
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
}
