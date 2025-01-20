/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2018, 2022, FabricMC
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

import java.util.Collection;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.TinyRemapper.LinkedMethodPropagation;
import net.fabricmc.tinyremapper.TinyRemapper.MrjState;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.api.TrRemapper;

class AsmRemapper extends TrRemapper {
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

		if (cls == null) {
			System.out.println(String.format("Warning: actual class missing: %1$s", owner));

			if (!tr.ignoreFieldDesc) {
				return tr.fieldMap.getOrDefault(owner + "/" + name + ";;" + desc, name);
			} else {
				return tr.fieldMap.getOrDefault(owner + "/" + name, name);
			}
		}

		return mapFieldName(cls, name, desc);
	}

	final String mapFieldName(ClassInstance cls, String name, String desc) {
		MemberInstance member = cls.resolve(TrMember.MemberType.FIELD, MemberInstance.getFieldId(name, desc, tr.ignoreFieldDesc));
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
		if (!desc.startsWith("(")) { // workaround for Remapper.mapValue calling mapMethodName even if the Handle is a field one
			return mapFieldName(owner, name, desc);
		}

		ClassInstance cls = getClass(owner);

		if (cls == null) {
			System.out.println(String.format("Warning: actual class missing: %1$s", owner));
			return tr.methodMap.getOrDefault(owner + "/" + name + desc, name);
		}

		return mapMethodName(cls, name, desc);
	}

	final String mapMethodName(ClassInstance cls, String name, String desc) {
		MemberInstance member = cls.resolve(TrMember.MemberType.METHOD, MemberInstance.getMethodId(name, desc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = tr.methodMap.get(cls.getName()+"/"+MemberInstance.getMethodId(name, desc))) == null || newName.equals(name);

		return tr.extraRemapper != null ? tr.extraRemapper.mapMethodName(cls.getName(), name, desc) : name;
	}

	@Override
	public String mapMethodNamePrefixDesc(String owner, String name, String descPrefix) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		Collection<TrMethod> members = cls.resolveMethods(name, descPrefix, true, null, null);
		MemberInstance member = members.size() == 1 ? (MemberInstance) members.iterator().next() : null;
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

	@Override
	public String mapMethodArg(String methodOwner, String methodName, String methodDesc, int lvIndex, String name) {
		String newName = tr.methodArgMap.get(methodOwner+"/"+MemberInstance.getMethodId(methodName, methodDesc)+lvIndex);
		if (newName != null) return newName;

		ClassInstance cls = getClass(methodOwner);
		if (cls == null) return name;

		MemberInstance originatingMethod = cls.resolve(TrMember.MemberType.METHOD, MemberInstance.getMethodId(methodName, methodDesc));
		if (originatingMethod == null) return name;

		String originatingNewName = tr.methodArgMap.get(originatingMethod.newNameOriginatingCls+"/"+MemberInstance.getMethodId(originatingMethod.name, originatingMethod.desc)+lvIndex);

		return originatingNewName != null ? originatingNewName : name;
	}

	public String mapMethodVar(String methodOwner, String methodName, String methodDesc, int lvIndex, int startOpIdx, int asmIndex, String name) {
		return name; // TODO: implement
	}

	@Override
	public String mapAnnotationAttributeName(String descriptor, String name) {
		throw new RuntimeException("Deprecated function");
	}

	@Override
	public String mapAnnotationAttributeName(final String annotationDesc, final String name, String attributeDesc) {
		String annotationClass = Type.getType(annotationDesc).getInternalName();

		if (attributeDesc == null) {
			return this.mapMethodNamePrefixDesc(annotationClass, name, "()");
		} else {
			return this.mapMethodName(annotationClass, name, "()" + attributeDesc);
		}
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
