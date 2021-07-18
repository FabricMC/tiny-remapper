package net.fabricmc.tinyremapper.api;

import java.util.Collection;

public interface TrClass {
	TrEnvironment getClasspath();

	int getAccess();

	String getName();

	String getSuperName();

	String getSignature();

	Collection<String> getInterfaces();

	Collection<? extends TrMember> getMembers();

	TrMember getField(String name, String desc);

	TrMember getMethod(String name, String desc);

	TrMember resolveField(String name, String desc);

	TrMember resolveMethod(String name, String desc);
}
