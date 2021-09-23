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

import net.fabricmc.tinyremapper.api.TrClass;

/**
 * @deprecated Not Implemented.
 * Only {@link OutputConsumer#acceptResource(Path, byte[])}
 * and {@link OutputConsumer#acceptClassFile(String, int, byte[])}
 * will be called in the internal implementation.
 */
@Deprecated
public interface OutputConsumer {
	void acceptResource(Path path, byte[] data) throws IOException;
	void acceptClassFile(Path path, byte[] data) throws IOException;
	default void acceptClassFile(String name, int mrjVersion, byte[] data) throws IOException {
		acceptClassFile(TrClass.getPathInJar(name, mrjVersion), data);
	}
}
