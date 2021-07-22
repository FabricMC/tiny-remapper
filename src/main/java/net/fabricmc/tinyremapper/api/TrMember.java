package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public interface TrMember {
	default boolean isField() {
		return getType() == MemberType.FIELD;
	}

	default boolean isMethod() {
		return getType() == MemberType.METHOD;
	}

	MemberType getType();

	TrClass getOwner();

	/**
	 * @see ClassVisitor#visitField(int, String, String, String, Object)
	 */
	String getName();
	String getNewName();

	/**
	 * @see ClassVisitor#visitField(int, String, String, String, Object)
	 */
	String getDesc();

	/**
	 * @see Opcodes
	 */
	int getAccess();

	/**
	 * Position inside the class relative to other members, in occurrence order.
	 */
	int getIndex();

	/**
	 * Declared public; may be accessed from outside its package.
	 */
	default boolean isPublic() {
		return (getAccess() & Opcodes.ACC_PUBLIC) != 0;
	}

	/**
	 * Declared protected; may be accessed within subclasses.
	 */
	default boolean isProtected() {
		return (getAccess() & Opcodes.ACC_PROTECTED) != 0;
	}

	/**
	 * Declared private; accessible only within the defining class and other classes belonging to the same nest (§5.4.4).
	 */
	default boolean isPrivate() {
		return (getAccess() & Opcodes.ACC_PRIVATE) != 0;
	}

	/**
	 * Declared static.
	 */
	default boolean isStatic() {
		return (getAccess() & Opcodes.ACC_STATIC) != 0;
	}

	/**
	 * Declared final; never directly assigned to after object construction / must not be overridden (§5.4.5).
	 */
	default boolean isFinal() {
		return (getAccess() & Opcodes.ACC_FINAL) != 0;
	}

	/**
	 * Declared synthetic; not present in the source code.
	 */
	default boolean isSynthetic() {
		return (getAccess() & Opcodes.ACC_SYNTHETIC) != 0;
	}

	enum MemberType {
		METHOD,
		FIELD
	}
}
