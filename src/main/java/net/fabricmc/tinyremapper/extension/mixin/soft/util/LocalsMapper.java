/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2026, FabricMC
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

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.tinyremapper.api.TrLocal;
import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;

public class LocalsMapper {
	public static String mapLocal(CommonData data, TrMethod target, String localName) {
		TrLocal[] localVariables = target.getLocals();

		if (localVariables == null || localVariables.length == 0) {
			return localName;
		}

		Map<String, Integer> lvtName2Index = new HashMap<>();

		for (TrLocal variable : localVariables) {
			if (!lvtName2Index.containsKey(variable.getName())) {
				lvtName2Index.put(variable.getName(), variable.getIndex());
			} else {
				lvtName2Index.put(variable.getName(), -1); // TODO actually generate lvt for injection points, currently only handles unique names
			}
		}

		if (!lvtName2Index.containsKey(localName)) {
			return localName;
		}

		int lvIndex = lvtName2Index.get(localName);

		if (lvIndex < 0) {
			return localName;
		}

		return data.mapper.asTrRemapper().mapMethodArg(target.getOwner().getName(), target.getName(), target.getDesc(), lvIndex, localName);
	}
}
