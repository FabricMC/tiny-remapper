package net.fabricmc.tinyremapper.api;

import net.fabricmc.tinyremapper.TinyRemapper;

public interface Classpath {
	/**
	 * @return the class with the passed name, or null if not found.
	 */
	ClassHeader getByName(String internalName);

	String mapType(String internalName);
}
