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

package net.fabricmc.tinyremapper.legacy;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.fabricmc.tinyremapper.api.io.InputSupplier;
import net.fabricmc.tinyremapper.util.DirectoryInputSupplier;
import net.fabricmc.tinyremapper.util.JarInputSupplier;

/**
 * This mimic the legacy behaviour of reading input.
 */
public final class PathInputSupplier implements InputSupplier {
	private final Path path;
	private final List<InputSupplier> inputs;

	public PathInputSupplier(Path path) throws IOException {
		this.path = path;
		this.inputs = new ArrayList<>();

		if (Files.isDirectory(path)) this.inputs.add(new DirectoryInputSupplier(path, EnumSet.of(FileType.CLASS_FILE)));

		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String fileName = file.getFileName().toString();

				if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) {
					PathInputSupplier.this.inputs.add(new JarInputSupplier(file, EnumSet.of(FileType.CLASS_FILE)));
				}

				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Override
	public String getSource() {
		return path.toString();
	}

	@Override
	public void open() throws IOException {
		for (InputSupplier input : this.inputs) {
			input.open();
		}
	}

	@Override
	public CompletableFuture<?> loadAsync(InputConsumer consumer, Executor executor) {
		CompletableFuture<?>[] futures = new CompletableFuture[this.inputs.size()];

		for (int i = 0; i < this.inputs.size(); i += 1) {
			futures[i] = this.inputs.get(i).loadAsync(consumer, executor);
		}

		return CompletableFuture.allOf(futures);
	}

	@Override
	public void close() throws IOException {
		for (InputSupplier input : this.inputs) {
			input.close();
		}
	}
}
