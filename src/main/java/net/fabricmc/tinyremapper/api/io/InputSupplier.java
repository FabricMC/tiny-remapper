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

package net.fabricmc.tinyremapper.api.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import net.fabricmc.tinyremapper.api.TrClass;

public interface InputSupplier {
	/**
	 * Only {@link InputConsumer#acceptResourceFile(Path, byte[])}
	 * and {@link InputConsumer#acceptClassFile(Path, byte[])}
	 * will be overwritten in the internal implementation.
	 */
	interface InputConsumer {
		/**
		 * @deprecated Unstable API
		 */
		void acceptResourceFile(Path path, byte[] data);
		void acceptClassFile(Path path, byte[] data);
		default void acceptClassFile(String name, int mrjVersion, byte[] data) {
			this.acceptClassFile(TrClass.getPathInJar(name, mrjVersion), data);
		}
	}

	/**
	 * Flags to indicate which type of file should be read. Only useful for sub-classes.
	 */
	enum FileType {
		CLASS_FILE, RESOURCE_FILE
	}

	/**
	 * Get the source of the input. Only useful for logging.
	 */
	String getSource();

	/**
	 * Must be called exactly once before {@link InputSupplier#load(InputConsumer)}.
	 */
	void open() throws IOException;

	/**
	 * Load files into {@link InputConsumer}. Only valid after {@link InputSupplier#open()}, before {@link InputSupplier#close()}.
	 */
	CompletableFuture<?> loadAsync(InputConsumer consumer, Executor executor);
	default void load(InputConsumer consumer) {
		// ForkJoinPool.commonPool() is the default executor in Java.
		loadAsync(consumer, ForkJoinPool.commonPool()).join();
	}

	/**
	 * Release any resources binding with this object.
	 */
	void close() throws IOException;
}
