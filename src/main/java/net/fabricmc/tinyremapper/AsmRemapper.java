/*
 * Copyright (C) 2016, 2018 Player, asie
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

import java.util.Map;

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.TinyRemapper.RClass;

class AsmRemapper extends Remapper {
	public AsmRemapper(TinyRemapper remapper) {
		this.remapper = remapper;
	}

	@Override
	public String map(String typeName) {
		String ret = remapper.classMap.get(typeName);

		return ret != null ? ret : typeName;
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		RClass cls = getClass(owner);
		if (cls == null) return name;

		String ret = cls.fieldsToMap.get(name + ";;" + desc);
		if (ret != null) return ret;

		assert remapper.fieldMap.get(owner+"/"+name+";;"+desc) == null;

		return name;
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		RClass cls = getClass(owner);
		if (cls == null) return name;

		String ret = cls.methodsToMap.get(name+desc);
		if (ret != null) return ret;

		assert remapper.methodMap.get(owner+"/"+name+desc) == null;
		return name;
	}

	public String mapLambdaInvokeDynamicMethodName(String owner, String name, String desc) {
		return mapMethodName(owner, name, desc);
	}

	public String mapArbitraryInvokeDynamicMethodName(String owner, String name) {
		RClass cls = getClass(owner);
		if (cls == null) return name;

		String match = null;

		for (Map.Entry<String, String> entry : cls.methodsToMap.entrySet()) {
			String src = entry.getKey();
			int descStart = src.indexOf('(');

			if (name.length() == descStart && name.equals(src.substring(0, descStart))) {
				if (match != null) { // mapping conflict
					match = null;
					break;
				}

				String dst = entry.getValue();
				match = dst.substring(0, dst.indexOf('('));
			}
		}

		if (match != null) {
			return match;
		} else {
			return name;
		}
	}

	private RClass getClass(String owner) {
		RClass ret = remapper.nodes.get(owner);
		if (ret != null) return ret;

		owner = remapper.classMap.get(owner);
		if (owner == null) return null;

		return remapper.nodes.get(owner);
	}

	private final TinyRemapper remapper;
}
