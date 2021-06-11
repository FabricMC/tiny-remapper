package net.fabricmc.tinyremapper.api;

public interface Classpath {
	/**
	 * @return the class with the passed name, or null if not found.
	 */
	ResolvedClass getByName(String internalName);
}
