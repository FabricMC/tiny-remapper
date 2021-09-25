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

package net.fabricmc.tinyremapper;

import java.util.Locale;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.api.TrMember;

public final class PackageAccessChecker {
	/**
	 * Check if a class can access a specific class, printing and recording failure for later.
	 */
	public static void checkClass(String accessingClass, String targetClass, String source, AsmRemapper remapper) {
		if (accessingClass.equals(targetClass)) { // self-access
			return;
		}

		ClassInstance targetCls = remapper.getClass(targetClass);
		if (targetCls == null) return;
		targetCls = targetCls.getMrjOrigin();

		// check if accessible via public, private or same class
		// private is fine since it can't have been influenced by remapping

		if (targetCls.isPublicOrPrivate()) {
			return;
		}

		// -> the class is only reachable from the same package
		// check for same package after mapping

		String mappedAccessor = remapper.map(accessingClass);
		int pkgEnd = mappedAccessor.lastIndexOf('/');
		String mappedTarget = remapper.map(targetClass);

		if (isSamePackage(mappedAccessor, pkgEnd, mappedTarget)) {
			return;
		}

		// target class is not public and in a different package
		// -> invalid access detected, needs to be public

		System.out.printf("Invalid access from %s in %s to package-private class %s after remapping.%n",
				source,
				mappedAccessor,
				mappedTarget);

		remapper.tr.classesToMakePublic.add(targetCls);
	}

	/**
	 * Check if all types in a descriptor can access a specific class, printing and recording failure for later.
	 */
	public static void checkDesc(String accessingClass, String targetDesc, String source, AsmRemapper remapper) {
		int startPos = 0;
		int pos;

		while ((pos = targetDesc.indexOf('L', startPos)) >= 0) {
			pos++;
			int end = targetDesc.indexOf(';', pos);
			if (end < 0) throw new IllegalArgumentException("invalid descriptor: ".concat(targetDesc));

			checkClass(accessingClass, targetDesc.substring(pos, end), source, remapper);

			startPos = end + 1;
		}
	}

	/**
	 * Check if all types in a value can access a specific class, printing and recording failure for later.
	 */
	public static void checkValue(String accessingClass, Object value, String source, AsmRemapper remapper) {
		if (value instanceof Type) {
			checkDesc(accessingClass, ((Type) value).getDescriptor(), source, remapper);
		} else if (value instanceof Handle) {
			Handle handle = (Handle) value;
			checkMember(accessingClass, handle.getOwner(), handle.getName(), handle.getDesc(), TrMember.MemberType.METHOD, source, remapper);
		} else if (value instanceof ConstantDynamic) {
			ConstantDynamic constantDynamic = (ConstantDynamic) value;

			for (int i = 0, max = constantDynamic.getBootstrapMethodArgumentCount(); i < max; i++) {
				checkValue(accessingClass, constantDynamic.getBootstrapMethodArgument(i), source, remapper);
			}

			checkValue(accessingClass, constantDynamic.getBootstrapMethod(), source, remapper);
			checkDesc(accessingClass, constantDynamic.getDescriptor(), source, remapper);
		}
	}

	/**
	 * Check if a class can access a specific member, printing and recording failure for later.
	 */
	public static void checkMember(String accessingOwner, String owner, String name, String desc, MemberType type, String source, AsmRemapper remapper) {
		checkDesc(accessingOwner, desc, source, remapper);

		ClassInstance cls = remapper.getClass(owner);
		if (cls == null) return;
		cls = cls.getMrjOrigin();

		String id = MemberInstance.getId(type, name, desc, remapper.tr.ignoreFieldDesc);
		MemberInstance member = cls.resolve(type, id);	// cls is already the correct version

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

		String mappedAccessor = remapper.map(accessingOwner);
		int pkgEnd = mappedAccessor.lastIndexOf('/');

		if (!clsAccessible && isSamePackage(mappedAccessor, pkgEnd, remapper.map(owner))) {
			if (memberAccessible || owner.equals(member.cls.getName())) { // both cls+member are accessible
				return;
			} else { // only the class is known to be accessible, further member accessibility testing follows
				clsAccessible = true;
			}
		}

		if (!memberAccessible && isSamePackage(mappedAccessor, pkgEnd, remapper.map(member.cls.getName()))) {
			if (clsAccessible) { // both cls+member are accessible
				return;
			} else {
				memberAccessible = true;
			}
		}

		// check for access to protected member in a super class

		if (member.isProtected() && hasSuperCls(accessingOwner, member.cls.getName(), remapper)) { // accessor in sub-class of accessed member
			// JVMS 5.4.4 is partially satisfied: D (=accessingOwner) is the same or a sub class of C (=member.cls)
			// still need to check that the access is static or doesn't go through a sibling branch: T (=owner) is same/super/sub of D
			// example: b extends a, c extends a: b can't access protected c.x even if x is declared in a, it can only access a.x or b.x
			if (member.isStatic()
					|| owner.equals(accessingOwner) || owner.equals(member.cls.getName()) // trivial cases
					|| hasSuperCls(owner, accessingOwner, remapper) || hasSuperCls(accessingOwner, owner, remapper)) {
				return;
			}
		}

		assert !clsAccessible || !memberAccessible;

		// target class or member is not public, in a different package and not in a super class
		// -> invalid access detected, needs to be public

		String mappedName, mappedDesc;

		if (type == TrMember.MemberType.FIELD) {
			mappedName = remapper.mapFieldName(owner, name, desc);
			mappedDesc = remapper.mapDesc(desc);
		} else {
			mappedName = remapper.mapMethodName(owner, name, desc);
			mappedDesc = remapper.mapMethodDesc(desc);
		}

		String inaccessible = null;

		if (!clsAccessible) {
			inaccessible = String.format("package-private class %s", remapper.map(owner));
		}

		if (!memberAccessible) {
			String memberMsg = String.format("%s %s %s/%s",
					member.isProtected() ? "protected" : "package-private",
							type.name().toLowerCase(Locale.ENGLISH),
							remapper.map(member.cls.getName()),
							MemberInstance.getId(type, mappedName, mappedDesc, remapper.tr.ignoreFieldDesc));

			if (inaccessible == null) {
				inaccessible = memberMsg;
			} else {
				inaccessible = String.format("%s, %s", inaccessible, memberMsg);
			}
		}

		System.out.printf("Invalid access from %s in %s to %s after remapping.%n",
				source,
				mappedAccessor,
				inaccessible);

		if (!clsAccessible) remapper.tr.classesToMakePublic.add(cls);
		if (!memberAccessible) remapper.tr.membersToMakePublic.add(member);
	}

	private static boolean isSamePackage(String clsA, int pkgEnd, String clsB) {
		return pkgEnd < 0 && clsB.indexOf('/') < 0 // both empty package
				|| pkgEnd >= 0 // both non-empty (considering prev condition)
				&& pkgEnd < clsB.length() // pkg not longer than whole other name
				&& clsB.charAt(pkgEnd) == '/' // potentially same prefix length
				&& clsB.indexOf('/', pkgEnd + 1) < 0 // definitely same prefix length
				&& clsA.regionMatches(0, clsB, 0, pkgEnd); // same prefix -> same package
	}

	private static boolean hasSuperCls(String cls, String reqSuperCls, AsmRemapper remapper) {
		assert !cls.equals(reqSuperCls);

		ClassInstance c;

		while ((c = remapper.getClass(cls)) != null && (cls = c.getSuperName()) != null) {
			if (cls.equals(reqSuperCls)) return true;
		}

		return false;
	}
}
