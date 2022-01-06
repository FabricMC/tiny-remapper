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
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import net.fabricmc.tinyremapper.FileSystemHandler;
import net.fabricmc.tinyremapper.api.io.InputSupplier;

public final class PathInputSupplier implements InputSupplier {
	public PathInputSupplier(Path path) throws IOException {
		this.path = path;
	}

	@Override
	public String getSource() {
		return path.toString();
	}

	@Override
	public CompletableFuture<?> read(Predicate<String> fileNameFilter, Executor executor, InputConsumer consumer) throws IOException {
		return read(path, fileNameFilter, true, executor, consumer);
	}

	static CompletableFuture<?> read(Path path, Predicate<String> fileNameFilter, boolean recurseZip, Executor executor, InputConsumer consumer) throws IOException {
		List<CompletableFuture<?>> futures = new ArrayList<>();

		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String fileName = file.getFileName().toString().toLowerCase(Locale.ENGLISH);

				if (recurseZip && (fileName.endsWith(".zip") || fileName.endsWith(".jar"))) {
					futures.add(readZip(file, fileNameFilter, executor, consumer));
				} else if (fileNameFilter.test(fileName)) {
					futures.add(CompletableFuture.runAsync(() -> {
						byte[] data;

						try {
							data = Files.readAllBytes(file);
						} catch (IOException e) {
							throw new UncheckedIOException("Error reading "+file, e);
						}

						consumer.accept(file, data);
					}, executor));
				}

				return FileVisitResult.CONTINUE;
			}
		});

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	static CompletableFuture<?> readZip(Path file, Predicate<String> fileNameFilter, Executor executor, InputConsumer consumer) throws IOException {
		if (Files.isDirectory(file)) throw new IOException("not a jar/zip file: "+file);

		FileSystem fs = FileSystemHandler.open(file);
		Path root = fs.getPath("/");

		return read(root, fileNameFilter, false, executor, consumer)
				.whenComplete((res, exc) -> {
					try {
						FileSystemHandler.close(fs);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
	}

	@Override
	public String toString() {
		return String.format("path %s", path);
	}

	private final Path path;
}
