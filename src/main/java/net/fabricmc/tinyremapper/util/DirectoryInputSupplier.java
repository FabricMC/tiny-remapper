/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, FabricMC
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

package net.fabricmc.tinyremapper.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.fabricmc.tinyremapper.api.io.InputSupplier;

public final class DirectoryInputSupplier implements InputSupplier {
	private final Path directory;
	private final Set<FileType> types;

	public DirectoryInputSupplier(Path directory) throws IOException {
		this(directory, EnumSet.allOf(FileType.class));
	}

	public DirectoryInputSupplier(Path directory, Set<FileType> types) throws IOException {
		if (!Files.exists(directory)) throw new IOException("Path " + directory + " does not exists.");
		if (!Files.isDirectory(directory)) throw new IOException("Path " + directory + " is not a directory.");

		this.directory = directory;
		this.types = types;
	}

	@Override
	public String getSource() {
		return "Directory:" + directory;
	}

	@Override
	public void open() { }

	@Override
	public CompletableFuture<?> loadAsync(InputConsumer consumer, Executor executor) {
		Collection<CompletableFuture<?>> futures = new ArrayList<>();

		try {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Path relativePath = directory.toAbsolutePath().relativize(file.toAbsolutePath());
					String fileName = file.getFileName().toString();

					if (fileName.endsWith(".class")) {
						if (types.contains(FileType.CLASS_FILE)) {
							futures.add(CompletableFuture.runAsync(() -> {
								try {
									consumer.acceptClassFile(relativePath, Files.readAllBytes(file));
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}, executor));
						}
					} else {
						if (types.contains(FileType.RESOURCE_FILE)) {
							futures.add(CompletableFuture.runAsync(() -> {
								try {
									consumer.acceptResourceFile(relativePath, Files.readAllBytes(file));
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}, executor));
						}
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	@Override
	public void close() { }
}
