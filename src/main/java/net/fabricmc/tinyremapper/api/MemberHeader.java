package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public interface MemberHeader {
	ClassHeader getOwner();

	/**
	 * @see Opcodes
	 */
	int getAccess();

	/**
	 * @see ClassVisitor#visitField(int, String, String, String, Object)
	 */
	String getName();

	/**
	 * @see ClassVisitor#visitField(int, String, String, String, Object)
	 */
	String getDesc();

	MemberType getType();

	enum MemberType {
		METHOD,
		FIELD
	}
}
