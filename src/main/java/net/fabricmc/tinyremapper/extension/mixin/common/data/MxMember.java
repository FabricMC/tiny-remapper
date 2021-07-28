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

import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;

public class MxMember {
	private final String owner;
	private final String name;
	private final String desc;

	MxMember(String owner, String name, String desc) {
		this.owner = Objects.requireNonNull(owner);
		this.name = Objects.requireNonNull(name);
		this.desc = Objects.requireNonNull(desc);
	}

	public String getName() {
		return name;
	}

	public String getDesc() {
		return desc;
	}

	public MemberType getType() {
		return StringUtility.getTypeByDesc(desc);
	}

	public MxClass getOwner() {
		return new MxClass(owner);
	}

	public TrMember asTrMember(ResolveUtility resolver) {
		return resolver.resolveMember(owner, name, desc, ResolveUtility.FLAG_UNIQUE)
				.orElseThrow(() -> new RuntimeException(String.format("Cannot convert %s %s %s to TrMember", owner, name, desc)));
	}
}
