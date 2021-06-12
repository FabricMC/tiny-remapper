package net.fabricmc.tinyremapper.api;

import java.util.List;

public interface ClassHeader {
	Classpath getClasspath();

	int getAccess();

	String getName();

	String getSuperName();

	String getSignature();

	List<String> getInterfaceList();

	/**
	 * get field by name and descriptor, this is not cached
	 */
	MemberHeader getField(String name, String desc);

	/**
	 * get method by name and descriptor, this is not cached
	 */
	MemberHeader getMethod(String name, String desc);

	Iterable<MemberHeader> allMembers();
}
