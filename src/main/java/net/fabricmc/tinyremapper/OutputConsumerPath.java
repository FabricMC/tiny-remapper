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

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class OutputConsumerPath implements BiConsumer<String, byte[]>, Closeable {
	public OutputConsumerPath(Path dstFile) throws IOException {
		if (!isJar(dstFile)) {
			Files.createDirectories(dstFile);

			dstDir = dstFile;
			closeFs = false;
			isJar = false;
		} else {
			createParentDirs(dstFile);

			URI uri;

			try {
				uri = new URI("jar:"+dstFile.toUri().toString());
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}

			Map<String, String> env = new HashMap<>();
			env.put("create", "true");

			dstDir = FileSystems.newFileSystem(uri, env).getPath("/");
			closeFs = true;
			isJar = true;
		}
	}

	public OutputConsumerPath(Path dstDir, boolean closeFs) {
		this.dstDir = dstDir;
		this.closeFs = closeFs;
		isJar = dstDir.getFileSystem().provider().getScheme().equals("jar");
	}

	public void addNonClassFiles(Path srcFile) throws IOException {
		if (Files.isDirectory(srcFile)) {
			addNonClassFiles(srcFile, false);
		} else if (Files.exists(srcFile)) {
			addNonClassFiles(FileSystems.newFileSystem(srcFile, null).getPath("/"), true);
		} else {
			throw new FileNotFoundException("file "+srcFile+" doesn't exist");
		}
	}

	public void addNonClassFiles(Path srcDir, boolean closeFs) throws IOException {
		try {
			Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (!file.toString().endsWith(classSuffix)) {
						Path dstFile = dstDir.resolve(srcDir.relativize(file).toString());

						createParentDirs(dstFile);
						Files.copy(file, dstFile, StandardCopyOption.REPLACE_EXISTING);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} finally {
			if (closeFs) srcDir.getFileSystem().close();
		}
	}

	@Override
	public void accept(String clsName, byte[] data) {
		try {
			Path dstFile = dstDir.resolve(clsName + classSuffix);

			if (isJar && Files.exists(dstFile)) {
				if (Files.isDirectory(dstFile)) throw new FileAlreadyExistsException("dst file "+dstFile+" is a directory");

				Files.delete(dstFile); // workaround for sporadic FileAlreadyExistsException (Files.write should overwrite, jdk bug?)
			}

			createParentDirs(dstFile);
			Files.write(dstFile, data);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void close() throws IOException {
		if (closeFs) dstDir.getFileSystem().close();
	}

	private static boolean isJar(Path path) {
		if (Files.exists(path)) {
			return !Files.isDirectory(path);
		}

		String name = path.getFileName().toString();

		return name.endsWith(".jar") || name.endsWith(".zip");
	}

	private static void createParentDirs(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent != null) Files.createDirectories(parent);
	}

	private static final String classSuffix = ".class";

	private final Path dstDir;
	private final boolean closeFs;
	private final boolean isJar;
}
