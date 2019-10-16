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

	/**
	 * Check if a class can access a specific member, printing and recording failure for later.
	 */
	public void checkPackageAccess(String accessingOwner, String owner, String name, String desc, MemberType type) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return;

		String id = MemberInstance.getId(type, name, desc);
		MemberInstance member = cls.resolve(type, id);

		if (member != null) {
			cls = member.cls;
			owner = cls.getName();
		}

		if (cls.isPublicOrPrivate() && (member == null || member.isPublicOrPrivate()) // public or private - no remapping induced accessibility change)
				|| accessingOwner.equals(owner)) { // same owner
			return;
		}

		// the class and/or member is only reachable from the same package or - if protected - from a sub class

		// check if same package after mapping, if yes return (nothing to do)

		String mappedAccessor = map(accessingOwner);
		String mappedTarget = map(owner);

		int pos = mappedAccessor.lastIndexOf('/');
		if (pos < 0 && mappedTarget.indexOf('/') < 0) return; // both empty package

		if (pos >= 0 // both non-empty (considering prev condition)
				&& pos < mappedTarget.length() // pkg not longer than whole other name
				&& mappedTarget.charAt(pos) == '/' // potentially same prefix length
				&& mappedTarget.indexOf('/', pos + 1) < 0 // definitely same prefix length
				&& mappedAccessor.regionMatches(0, mappedTarget, 0, pos - 1)) { // same prefix -> same package
			return;
		}

		// check for access to protected member in a super class

		if (cls.isPublicOrPrivate() && member != null && member.isProtected()) {
			ClassInstance accessingCls = getClass(accessingOwner);

			while (accessingCls != null && accessingCls.getSuperName() != null) {
				if (accessingCls.getSuperName().equals(owner)) { // accessor in sub-class of accessed member
					return;
				}

				accessingCls = getClass(accessingCls.getSuperName());
			}
		}

		// invalid access detected

		String mappedName, mappedDesc;

		if (type == MemberType.FIELD) {
			mappedName = mapFieldName(owner, name, desc);
			mappedDesc = mapDesc(desc);
		} else {
			mappedName = mapMethodName(owner, name, desc);
			mappedDesc = mapMethodDesc(desc);
		}

		System.out.printf("Invalid access from %s to %s/%s after remapping.%n",
				mappedAccessor,
				mappedTarget, MemberInstance.getId(type, mappedName, mappedDesc));

		if (!cls.isPublicOrPrivate()) remapper.classesToMakePublic.add(cls);
		if (member != null && !member.isPublicOrPrivate()) remapper.membersToMakePublic.add(member);
	}

	private ClassInstance getClass(String owner) {
		return remapper.classes.get(owner);
	}

	private final TinyRemapper remapper;
}
