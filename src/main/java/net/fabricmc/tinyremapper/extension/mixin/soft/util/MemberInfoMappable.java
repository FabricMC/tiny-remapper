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

package net.fabricmc.tinyremapper.extension.mixin.soft.util;

import java.util.Objects;
import java.util.Optional;

import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;
import net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo;

public class MemberInfoMappable implements IMappable<MemberInfo> {
	private final CommonData data;
	private final MemberInfo info;

	public MemberInfoMappable(CommonData data, MemberInfo info) {
		this.data = Objects.requireNonNull(data);
		this.info = Objects.requireNonNull(info);
	}

	@Override
	public MemberInfo result() {
		if (!info.isFullyQualified()) {
			data.logger.warn(String.format(Message.NOT_FULLY_QUALIFIED, info));
			return info;
		}

		Optional<TrMember> resolved = data.resolver.resolveMember(info.getOwner(), info.getName(), info.getDesc(), ResolveUtility.FLAG_UNIQUE | ResolveUtility.FLAG_RECURSIVE);

		if (resolved.isPresent()) {
			String newOwner = data.mapper.asTrRemapper().map(info.getOwner());
			String newName = data.mapper.mapName(resolved.get());
			String newDesc = data.mapper.mapDesc(resolved.get());

			return new MemberInfo(newOwner, newName, info.getQuantifier(), newDesc);
		} else {
			return info;
		}
	}
}
