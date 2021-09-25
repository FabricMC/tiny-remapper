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

package net.fabricmc.tinyremapper.extension.mixin.common.data;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;

public class MxClass {
	private final String name;

	public MxClass(String name) {
		this.name = Objects.requireNonNull(name);
	}

	public String getName() {
		return name;
	}

	public MxMember getField(String name, String desc) {
		return new MxMember(this.name, name, desc);
	}

	public MxMember getMethod(String name, String desc) {
		return new MxMember(this.name, name, desc);
	}

	public TrClass asTrClass(ResolveUtility resolver) {
		return resolver.resolveClass(name)
				.orElseThrow(() -> new RuntimeException(String.format("Cannot convert %s to TrClass.", name)));
	}
}
