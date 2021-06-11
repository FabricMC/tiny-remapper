package net.fabricmc.tinyremapper.api;

import java.util.List;

import net.fabricmc.tinyremapper.TinyRemapper;

public interface ClassHeader {
	Classpath getClasspath();

	int getAccess();

	String getName();

	String getSuperName();

	String getSignature();

	List<String> getInterfaceList();
}
