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

import org.objectweb.asm.Opcodes;

public interface TrField extends TrMember {
	/**
	 * Declared volatile; cannot be cached.
	 */
	default boolean isVolatile() {
		return getType().equals(MemberType.FIELD) && (getAccess() & Opcodes.ACC_VOLATILE) != 0;
	}

	/**
	 * Declared as an element of an enum class.
	 */
	default boolean isEnum() {
		return getType().equals(MemberType.FIELD) && (getAccess() & Opcodes.ACC_ENUM) != 0;
	}
}
