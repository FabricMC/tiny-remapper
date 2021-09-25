/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2020, 2021, FabricMC
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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.TinyRemapper.MrjState;

final class BridgeHandler {
	public static MemberInstance getTarget(MemberInstance bridgeMethod) {
		assert bridgeMethod.isBridge();

		MemberInstance ret = bridgeMethod.bridgeTarget;
		if (ret != null) return ret;

		// try to propagate bridge method mapping to the actual implementation

		String bridgeId = bridgeMethod.getId();
		int descStart = bridgeId.indexOf('(');

		for (MemberInstance m : bridgeMethod.cls.getMembers()) {
			if (m != bridgeMethod // same method
					&& m.isVirtual() // not a method or not relevant
					&& !m.isBridge() // method is a bridge on its own
					&& isBridged(bridgeId, m.getId(), descStart, bridgeMethod.getContext())) {
				bridgeMethod.bridgeTarget = m;

				return m;
			}
		}

		return null;
	}

	/**
	 * Determine whether a method is the target of a bridge from the descriptors.
	 *
	 * <p>This requires both methods to have the same name, same parameter count and all args+return value in the
	 * target method to be assignable to the bridge method's equivalents. The target method specializes the bridge
	 * method to provide an unchecked parameterized implementation and/or an override with a more specific return type.
	 */
	private static boolean isBridged(String bridgeId, String targetId, int descStart, MrjState context) {
		// check for same method name
		if (!bridgeId.regionMatches(0, targetId, 0, descStart + 1)) return false; // comparison includes ( to reject name suffixes

		// check for same or assignable return type
		int argsEndBridge = bridgeId.lastIndexOf(')');
		int argsEndTarget = targetId.lastIndexOf(')');

		if (!ClassInstance.isAssignableFrom(bridgeId, argsEndBridge + 1, targetId, argsEndTarget + 1, context)) {
			return false;
		}

		// check for same or assignable arg types
		int posBridge = descStart + 1;
		int posTarget = posBridge;

		for (; posBridge < argsEndBridge && posTarget < argsEndTarget; posBridge++, posTarget++) { // loops one arg at a time
			if (!ClassInstance.isAssignableFrom(bridgeId, posBridge, targetId, posTarget, context)) return false;

			// seek to the arg's last character (skip array prefixes and object descriptors)..
			// .. for bridgeId

			char type = bridgeId.charAt(posBridge);

			while (type == '[') {
				type = bridgeId.charAt(++posBridge);
			}

			if (type == 'L') posBridge = bridgeId.indexOf(';', posBridge + 1);

			// .. and for targetId

			type = targetId.charAt(posTarget);

			while (type == '[') {
				type = targetId.charAt(++posTarget);
			}

			if (type == 'L') posTarget = targetId.indexOf(';', posTarget + 1);
		}

		return posBridge == argsEndBridge && posTarget == argsEndTarget; // check for same arg count
	}

	public static void generateCompatBridges(ClassInstance cls, AsmRemapper remapper, ClassVisitor out) {
		memberLoop: for (MemberInstance m : cls.getMembers()) {
			String bridgedName = m.getNewBridgedName();
			String mappedName;

			if (bridgedName == null
					|| (mappedName = m.getNewMappedName()) == null
					|| bridgedName.equals(mappedName)) {
				continue;
			}

			for (MemberInstance o : cls.getMembers()) {
				if (o != m
						&& o.desc.equals(m.desc)
						&& remapper.mapMethodName(cls.getName(), o.name, o.desc).equals(mappedName)) {
					// nameDesc is already in use, skip generating bridge for it
					continue memberLoop;
				}
			}

			String mappedDesc = remapper.mapDesc(m.desc);
			int lvSize = 1;

			MethodVisitor mv = out.visitMethod(m.access | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC, mappedName, mappedDesc, null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);

			if (!mappedDesc.startsWith("()")) {
				for (Type type : Type.getArgumentTypes(mappedDesc)) {
					mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), lvSize);
					lvSize += type.getSize();
				}
			}

			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, remapper.map(cls.getName()), bridgedName, mappedDesc, cls.isInterface());

			Type retType = Type.getReturnType(mappedDesc);
			mv.visitInsn(retType.getOpcode(Opcodes.IRETURN));

			mv.visitMaxs(Math.max(lvSize, retType.getSize()), lvSize);
			mv.visitEnd();
		}
	}
}
