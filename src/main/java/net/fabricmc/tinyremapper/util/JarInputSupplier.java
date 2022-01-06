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
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import net.fabricmc.tinyremapper.api.io.InputSupplier;

public class JarInputSupplier implements InputSupplier {
	public JarInputSupplier(Path path) {
		this.path = path;
	}

	@Override
	public String getSource() {
		return path.toString();
	}

	@Override
	public CompletableFuture<?> read(Predicate<String> fileNameFilter, Executor executor, InputConsumer consumer) throws IOException {
		return PathInputSupplier.readZip(path, fileNameFilter, executor, consumer);
	}

	@Override
	public String toString() {
		return String.format("jar %s", path);
	}

	private final Path path;
}
