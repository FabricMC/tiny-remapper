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

import org.objectweb.asm.commons.Remapper;

public abstract class TrRemapper extends Remapper {
	/**
	 * remaps a method when the full descriptor is unknown.
	 * @param owner the owner of the method
	 * @param name the name of the method
	 * @param descPrefix the part of the descriptor that is known (must be the start)
	 * @return the mapped name
	 */
	public abstract String mapMethodNamePrefixDesc(String owner, String name, String descPrefix);

	/**
	 * remaps a parameter name.
	 * @param lvIndex the local variable index of the arg
	 */
	public abstract String mapMethodArg(String methodOwner, String methodName, String methodDesc, int lvIndex, String name);

	public abstract String mapMethodVar(String methodOwner, String methodName, String methodDesc, int lvIndex, int startOpIdx, int asmIndex, String name);

	/**
	 * @deprecated Please use {@link TrRemapper#mapAnnotationAttributeName(String, String, String)}
	 */
	@Deprecated
	@Override
	public String mapAnnotationAttributeName(String descriptor, String name) {
		return super.mapAnnotationAttributeName(descriptor, name);
	}

	public abstract String mapAnnotationAttributeName(String annotationDesc, String name, String attributeDesc);
}
