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

package net.fabricmc.tinyremapper.api;

public interface TrEnvironment {
	int getMrjVersion();
	TrRemapper getRemapper();

	/**
	 * @return the class with the passed name, or null if not found.
	 */
	TrClass getClass(String internalName);

	default TrField getField(String owner, String name, String desc) {
		TrClass cls = getClass(owner);

		return cls != null ? cls.getField(name, desc) : null;
	}

	default TrMethod getMethod(String owner, String name, String desc) {
		TrClass cls = getClass(owner);

		return cls != null ? cls.getMethod(name, desc) : null;
	}

	void propagate(TrMember member, String newName);
}
