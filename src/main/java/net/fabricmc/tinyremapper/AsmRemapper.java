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

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.MemberInstance.MemberType;

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
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		MemberInstance member = cls.resolve(MemberType.FIELD, MemberInstance.getFieldId(name, desc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = remapper.fieldMap.get(owner+"/"+MemberInstance.getFieldId(name, desc))) == null || newName.equals(name);

		return name;
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		MemberInstance member = cls.resolve(MemberType.METHOD, MemberInstance.getMethodId(name, desc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = remapper.methodMap.get(owner+"/"+MemberInstance.getMethodId(name, desc))) == null || newName.equals(name);

		return name;
	}

	public String mapMethodNamePrefixDesc(String owner, String name, String descPrefix) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		MemberInstance member = cls.resolvePartial(MemberType.METHOD, name,descPrefix);
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		return name;
	}

	public String mapLambdaInvokeDynamicMethodName(String owner, String name, String desc) {
		return mapMethodName(owner, name, desc);
	}

	public String mapArbitraryInvokeDynamicMethodName(String owner, String name) {
		return mapMethodNamePrefixDesc(owner, name, null);
	}

	public String mapMethodArg(String methodOwner, String methodName, String methodDesc, int lvIndex, String name) {
		String newName = remapper.methodArgMap.get(methodOwner+"/"+MemberInstance.getMethodId(methodName, methodDesc)+lvIndex);
		if (newName != null) return newName;

		ClassInstance cls = getClass(methodOwner);
		if (cls == null) return name;

		MemberInstance originatingMethod = cls.resolve(MemberType.METHOD, MemberInstance.getMethodId(methodName, methodDesc));
		if (originatingMethod == null) return name;

		String originatingNewName = remapper.methodArgMap.get(originatingMethod.newNameOriginatingCls+"/"+MemberInstance.getMethodId(originatingMethod.name, originatingMethod.desc)+lvIndex);

		return originatingNewName != null ? originatingNewName : name;
	}

	private ClassInstance getClass(String owner) {
		return remapper.classes.get(owner);
	}

	private final TinyRemapper remapper;
}
