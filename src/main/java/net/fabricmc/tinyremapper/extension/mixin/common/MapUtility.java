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

package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrRemapper;

public final class MapUtility {
	private final TrRemapper remapper;
	private final Logger logger;

	public static final List<String> IGNORED_NAME = Arrays.asList("<init>", "<clinit>");

	public MapUtility(TrRemapper remapper, Logger logger) {
		this.remapper = Objects.requireNonNull(remapper);
		this.logger = Objects.requireNonNull(logger);
	}

	public String mapName(TrClass _class) {
		return remapper.map(_class.getName());
	}

	public String mapName(TrMember member) {
		if (member.isField()) {
			return remapper.mapFieldName(member.getOwner().getName(), member.getName(), member.getDesc());
		} else {
			return remapper.mapMethodName(member.getOwner().getName(), member.getName(), member.getDesc());
		}
	}

	public String mapDesc(TrClass _class) {
		return StringUtility.classNameToDesc(mapName(_class));
	}

	public String mapDesc(TrMember member) {
		if (member.isField()) {
			return remapper.mapDesc(member.getDesc());
		} else {
			return remapper.mapMethodDesc(member.getDesc());
		}
	}

	public String mapOwner(TrMember member) {
		return mapName(member.getOwner());
	}

	public TrRemapper asTrRemapper() {
		return this.remapper;
	}
}
