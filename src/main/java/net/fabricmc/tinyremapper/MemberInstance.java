package net.fabricmc.tinyremapper;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.objectweb.asm.Opcodes;

public final class MemberInstance {
	MemberInstance(MemberInstance.MemberType type, ClassInstance cls, String name, String desc, int access) {
		this.type = type;
		this.cls = cls;
		this.name = name;
		this.desc = desc;
		this.access = access;
	}

	public String getId() {
		if (type == MemberType.METHOD) {
			return getMethodId(name, desc);
		} else {
			return getFieldId(name, desc);
		}
	}

	public boolean isVirtual() {
		return type == MemberType.METHOD && (access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0;
	}

	public String getNewName() {
		return newName;
	}

	public boolean setNewName(String name) {
		if (name == null) throw new NullPointerException("null name");

		boolean ret = newNameUpdater.compareAndSet(this, null, name);

		return ret || name.equals(newName);
	}

	public void forceSetNewName(String name) {
		newName = name;
	}

	public static String getId(MemberType type, String name, String desc) {
		return type == MemberType.METHOD ? getMethodId(name, desc) : getFieldId(name, desc);
	}

	public static String getMethodId(String name, String desc) {
		return name.concat(desc);
	}

	public static String getFieldId(String name, String desc) {
		return name+";;"+desc;
	}

	public static String getNameFromId(MemberType type, String id) {
		String separator = type == MemberType.METHOD ? "(" : ";;";

		return id.substring(0, id.lastIndexOf(separator));
	}

	enum MemberType {
		METHOD,
		FIELD
	}

	private static final AtomicReferenceFieldUpdater<MemberInstance, String> newNameUpdater = AtomicReferenceFieldUpdater.newUpdater(MemberInstance.class, String.class, "newName");

	final MemberInstance.MemberType type;
	final ClassInstance cls;
	final String name;
	final String desc;
	final int access;
	private volatile String newName;
	String newNameOriginatingCls;
}