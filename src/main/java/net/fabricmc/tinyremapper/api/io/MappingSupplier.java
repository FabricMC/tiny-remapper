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

/**
 * @deprecated Not Implemented.
 */
@Deprecated
public interface MappingSupplier {
	String getSource();
	void load(MappingConsumer consumer) throws IOException;

	interface MappingConsumer {
		void acceptClass(String srcName, String dstName);
		void acceptMethod(String owner, String srcName, String desc, String dstName);
		void acceptMethodArg(String owner, String srcName, String desc, int lvIndex, String dstName);
		void acceptMethodVar(String owner, String srcName, String desc, int lvIndex, int startOpIdx, int asmIndex, String dstName);
		void acceptField(String owner, String srcName, String desc, String dstName);
	}
}
