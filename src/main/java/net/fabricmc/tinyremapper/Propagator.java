package net.fabricmc.tinyremapper;

import java.util.Set;

import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.TinyRemapper.Direction;
import net.fabricmc.tinyremapper.TinyRemapper.LinkedMethodPropagation;
import net.fabricmc.tinyremapper.api.TrMember;

final class Propagator {
	static void propagate(MemberInstance member, String memberId, String nameDst, Set<ClassInstance> visitedUp, Set<ClassInstance> visitedDown) {
		ClassInstance cls = member.cls;
		boolean isVirtual = member.isVirtual();

		visitedUp.add(cls);
		visitedDown.add(cls);
		cls.propagate(member.type, cls.getName(), memberId, nameDst,
				(isVirtual ? Direction.ANY : Direction.DOWN), isVirtual, false,
				true, visitedUp, visitedDown);
		visitedUp.clear();
		visitedDown.clear();

		if (cls.tr.propagateRecordComponents != LinkedMethodPropagation.DISABLED
				&& cls.isRecord()
				&& member.isField()
				&& (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) == (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) { // not static, but private+final
			String getterIdSrc = MemberInstance.getMethodId(member.name, "()".concat(member.desc));
			MemberInstance getter = cls.getMember(TrMember.MemberType.METHOD, getterIdSrc);

			if (getter != null && getter.isVirtual()) {
				visitedUp.add(cls);
				visitedDown.add(cls);
				cls.propagate(TrMember.MemberType.METHOD, cls.getName(), getterIdSrc, nameDst, Direction.ANY, true, true, true, visitedUp, visitedDown);
				visitedUp.clear();
				visitedDown.clear();
			}
		}
	}
}
