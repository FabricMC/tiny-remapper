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

import java.util.Locale;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.MemberInstance.MemberType;
import net.fabricmc.tinyremapper.TinyRemapper.BridgePropagation;

class AsmRemapper extends Remapper {
	public AsmRemapper(TinyRemapper remapper) {
		this.remapper = remapper;
	}

	@Override
	public String map(String typeName) {
		String ret = remapper.classMap.get(typeName);
		if (ret != null) return ret;

		return remapper.extraRemapper != null ? remapper.extraRemapper.map(typeName) : typeName;
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		MemberInstance member = cls.resolve(MemberType.FIELD, MemberInstance.getFieldId(name, desc, remapper.ignoreFieldDesc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = remapper.fieldMap.get(owner+"/"+MemberInstance.getFieldId(name, desc, remapper.ignoreFieldDesc))) == null || newName.equals(name);

		return remapper.extraRemapper != null ? remapper.extraRemapper.mapFieldName(owner, name, desc) : name;
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name; // TODO: try to map these from just the mappings?, warn if actual class is missing

		MemberInstance member = cls.resolve(MemberType.METHOD, MemberInstance.getMethodId(name, desc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = remapper.methodMap.get(owner+"/"+MemberInstance.getMethodId(name, desc))) == null || newName.equals(name);

		return remapper.extraRemapper != null ? remapper.extraRemapper.mapMethodName(owner, name, desc) : name;
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
		String newName = remapper.methodArgMap.get(methodOwner+"/"+MemberInstance.getMethodId(methodName, methodDesc)+lvIndex);
		if (newName != null) return newName;

		ClassInstance cls = getClass(methodOwner);
		if (cls == null) return name;

		MemberInstance originatingMethod = cls.resolve(MemberType.METHOD, MemberInstance.getMethodId(methodName, methodDesc));
		if (originatingMethod == null) return name;

		String originatingNewName = remapper.methodArgMap.get(originatingMethod.newNameOriginatingCls+"/"+MemberInstance.getMethodId(originatingMethod.name, originatingMethod.desc)+lvIndex);

		return originatingNewName != null ? originatingNewName : name;
	}

	public String mapMethodVar(String methodOwner, String methodName, String methodDesc, int lvIndex, int startOpIdx, int asmIndex, String name) {
		return name; // TODO: implement
	}

	void finish(String className, ClassVisitor cv) {
		if (remapper.propagateBridges == BridgePropagation.COMPATIBLE) {
			ClassInstance cls = getClass(className);

			if (cls != null) {
				BridgeHandler.generateCompatBridges(cls, this, cv);
			}
		}
	}

	/**
	 * Check if a class can access a specific member, printing and recording failure for later.
	 */
	public void checkPackageAccess(String accessingOwner, String owner, String name, String desc, MemberType type) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return;

		String id = MemberInstance.getId(type, name, desc, remapper.ignoreFieldDesc);
		MemberInstance member = cls.resolve(type, id);

		if (member == null) {
			// should be just missing super classes/interfaces from the analyzed class path, especially java ones
			// -> assume the access is still valid after remapping
			/*System.out.printf("Can't find member %s/%s accessed from %s.%n",
					owner, MemberInstance.getId(type, name, desc, remapper.ignoreFieldDesc), accessingOwner);*/
			return;
		}

		// check if accessible via public, private or same class
		// private is fine since it can't have been influenced by remapping
		boolean clsAccessible = cls.isPublicOrPrivate() || accessingOwner.equals(owner);
		boolean memberAccessible = member.isPublicOrPrivate() || accessingOwner.equals(member.cls.getName());

		if (clsAccessible && memberAccessible) { // trivially accessible
			return;
		}

		// -> the class and/or member is only reachable from the same package or - if protected - from a sub class

		// check for same package after mapping

		String mappedAccessor = map(accessingOwner);
		int pkgEnd = mappedAccessor.lastIndexOf('/');

		if (!clsAccessible && isSamePackage(mappedAccessor, pkgEnd, map(owner))) {
			if (memberAccessible || owner.equals(member.cls.getName())) { // both cls+member are accessible
				return;
			} else { // only the class is known to be accessible, further member accessibility testing follows
				clsAccessible = true;
			}
		}

		if (!memberAccessible && isSamePackage(mappedAccessor, pkgEnd, map(member.cls.getName()))) {
			if (clsAccessible) { // both cls+member are accessible
				return;
			} else {
				memberAccessible = true;
			}
		}

		// check for access to protected member in a super class

		if (member.isProtected() && hasSuperCls(accessingOwner, member.cls.getName())) { // accessor in sub-class of accessed member
			// JVMS 5.4.4 is partially satisfied: D (=accessingOwner) is the same or a sub class of C (=member.cls)
			// still need to check that the access is static or doesn't go through a sibling branch: T (=owner) is same/super/sub of D
			// example: b extends a, c extends a: b can't access protected c.x even if x is declared in a, it can only access a.x or b.x
			if (member.isStatic()
					|| owner.equals(accessingOwner) || owner.equals(member.cls.getName()) // trivial cases
					|| hasSuperCls(owner, accessingOwner) || hasSuperCls(accessingOwner, owner)) {
				return;
			}
		}

		assert !clsAccessible || !memberAccessible;

		// target class or member is not public, in a different package and not in a super class
		// -> invalid access detected, needs to be public

		String mappedName, mappedDesc;

		if (type == MemberType.FIELD) {
			mappedName = mapFieldName(owner, name, desc);
			mappedDesc = mapDesc(desc);
		} else {
			mappedName = mapMethodName(owner, name, desc);
			mappedDesc = mapMethodDesc(desc);
		}

		String inaccessible = null;

		if (!clsAccessible) {
			inaccessible = String.format("package-private class %s", map(owner));
		}

		if (!memberAccessible) {
			String memberMsg = String.format("%s %s %s/%s",
					member.isProtected() ? "protected" : "package-private",
							type.name().toLowerCase(Locale.ENGLISH),
							map(member.cls.getName()),
							MemberInstance.getId(type, mappedName, mappedDesc, remapper.ignoreFieldDesc));

			if (inaccessible == null) {
				inaccessible = memberMsg;
			} else {
				inaccessible = String.format("%s, %s", inaccessible, memberMsg);
			}
		}

		System.out.printf("Invalid access from %s to %s after remapping.%n",
				mappedAccessor,
				inaccessible);

		if (!clsAccessible) remapper.classesToMakePublic.add(cls);
		if (!memberAccessible) remapper.membersToMakePublic.add(member);
	}

	private boolean isSamePackage(String clsA, int pkgEnd, String clsB) {
		return pkgEnd < 0 && clsB.indexOf('/') < 0 // both empty package
				|| pkgEnd >= 0 // both non-empty (considering prev condition)
				&& pkgEnd < clsB.length() // pkg not longer than whole other name
				&& clsB.charAt(pkgEnd) == '/' // potentially same prefix length
				&& clsB.indexOf('/', pkgEnd + 1) < 0 // definitely same prefix length
				&& clsA.regionMatches(0, clsB, 0, pkgEnd - 1); // same prefix -> same package
	}

	private boolean hasSuperCls(String cls, String reqSuperCls) {
		assert !cls.equals(reqSuperCls);

		ClassInstance c;

		while ((c = getClass(cls)) != null && (cls = c.getSuperName()) != null) {
			if (cls.equals(reqSuperCls)) return true;
		}

		return false;
	}

	private ClassInstance getClass(String owner) {
		return remapper.classes.get(owner);
	}

	private final TinyRemapper remapper;
}
