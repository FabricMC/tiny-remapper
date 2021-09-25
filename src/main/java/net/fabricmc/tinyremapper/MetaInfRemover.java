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

package net.fabricmc.tinyremapper;

import java.io.InputStream;
import java.nio.file.Path;

public class MetaInfRemover implements OutputConsumerPath.ResourceRemapper {
	public static final MetaInfRemover INSTANCE = new MetaInfRemover();

	protected MetaInfRemover() { }

	@Override
	public boolean canTransform(TinyRemapper remapper, Path relativePath) {
		return relativePath.startsWith("META-INF") && relativePath.getNameCount() != 2;
	}

	@Override
	public void transform(Path destinationDirectory, Path relativePath, InputStream input, TinyRemapper remapper) {
	}
}
