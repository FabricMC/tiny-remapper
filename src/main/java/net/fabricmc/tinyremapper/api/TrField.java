package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.Opcodes;

public interface TrField extends TrMember {
	/**
	 * Declared volatile; cannot be cached.
	 */
	default boolean isVolatile() {
		return getType().equals(MemberType.FIELD) && (getAccess() & Opcodes.ACC_VOLATILE) != 0;
	}

	/**
	 * Declared as an element of an enum class.
	 */
	default boolean isEnum() {
		return getType().equals(MemberType.FIELD) && (getAccess() & Opcodes.ACC_ENUM) != 0;
	}
}
