package net.fabricmc.tinyremapper.api;

public interface TrEnvironment {
	/**
	 * @return the class with the passed name, or null if not found.
	 */
	TrClass getClass(String internalName);

	int getMrjVersion();
}
