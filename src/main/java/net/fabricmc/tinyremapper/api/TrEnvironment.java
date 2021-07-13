package net.fabricmc.tinyremapper.api;

public interface TrEnvironment {
	/**
	 * @return the class with the passed name, or null if not found.
	 */
	TrClass getByName(String internalName);

	String mapType(String internalName);
}
