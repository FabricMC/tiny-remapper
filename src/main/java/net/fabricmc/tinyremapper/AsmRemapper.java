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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.MemberInstance.MemberType;
import net.fabricmc.tinyremapper.TinyRemapper.LinkedMethodPropagation;
import net.fabricmc.tinyremapper.TinyRemapper.MrjState;

class AsmRemapper extends Remapper {
	AsmRemapper(MrjState context) {
		this.context = context;
		this.tr = context.tr;
	}

	@Override
	public String map(String typeName) {
		String ret = tr.classMap.get(typeName);
		if (ret != null) return ret;

		return tr.extraRemapper != null ? tr.extraRemapper.map(typeName) : typeName;
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		return mapFieldName(cls, name, desc);
	}

	final String mapFieldName(ClassInstance cls, String name, String desc) {
		MemberInstance member = cls.resolve(MemberType.FIELD, MemberInstance.getFieldId(name, desc, tr.ignoreFieldDesc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = tr.fieldMap.get(cls.getName()+"/"+MemberInstance.getFieldId(name, desc, tr.ignoreFieldDesc))) == null || newName.equals(name);

		return tr.extraRemapper != null ? tr.extraRemapper.mapFieldName(cls.getName(), name, desc) : name;
	}

	@Override
	public String mapRecordComponentName(String owner, String name, String descriptor) {
		return mapFieldName(owner, name, descriptor);
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name; // TODO: try to map these from just the mappings?, warn if actual class is missing

		return mapMethodName(cls, name, desc);
	}

	final String mapMethodName(ClassInstance cls, String name, String desc) {
		MemberInstance member = cls.resolve(MemberType.METHOD, MemberInstance.getMethodId(name, desc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = tr.methodMap.get(cls.getName()+"/"+MemberInstance.getMethodId(name, desc))) == null || newName.equals(name);

		return tr.extraRemapper != null ? tr.extraRemapper.mapMethodName(cls.getName(), name, desc) : name;
	}

	public String mapMethodNamePrefixDesc(String owner, String name, String descPrefix) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		MemberInstance member = cls.resolvePartial(MemberType.METHOD, name, descPrefix);
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
		String newName = tr.methodArgMap.get(methodOwner+"/"+MemberInstance.getMethodId(methodName, methodDesc)+lvIndex);
		if (newName != null) return newName;

		ClassInstance cls = getClass(methodOwner);
		if (cls == null) return name;

		MemberInstance originatingMethod = cls.resolve(MemberType.METHOD, MemberInstance.getMethodId(methodName, methodDesc));
		if (originatingMethod == null) return name;

		String originatingNewName = tr.methodArgMap.get(originatingMethod.newNameOriginatingCls+"/"+MemberInstance.getMethodId(originatingMethod.name, originatingMethod.desc)+lvIndex);

		return originatingNewName != null ? originatingNewName : name;
	}

	public String mapMethodVar(String methodOwner, String methodName, String methodDesc, int lvIndex, int startOpIdx, int asmIndex, String name) {
		return name; // TODO: implement
	}

	void finish(String className, ClassVisitor cv) {
		ClassInstance cls = null;

		if (tr.propagateBridges == LinkedMethodPropagation.COMPATIBLE
				|| tr.propagateRecordComponents == LinkedMethodPropagation.COMPATIBLE) {
			cls = getClass(className);

			if (cls != null) {
				BridgeHandler.generateCompatBridges(cls, this, cv);
			}
		}
	}

	ClassInstance getClass(String owner) {
		return context.getClass(owner);
	}

	final MrjState context;
	final TinyRemapper tr;
}
