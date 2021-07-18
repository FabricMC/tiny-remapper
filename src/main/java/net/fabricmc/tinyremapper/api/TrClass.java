package net.fabricmc.tinyremapper.api;

import java.util.Collection;
import java.util.List;

public interface TrClass {
	TrEnvironment getClasspath();

	int getAccess();

	String getName();

	String getSuperName();

	String getSignature();

	List<String> getInterfaces();

	/**
	 * @param desc can be null
	 */
	TrMember resolveField(String name, String desc);

	/**
	 */
	TrMember resolveMethod(String name, String desc);

	Collection<? extends TrMember> getMembers();
}
