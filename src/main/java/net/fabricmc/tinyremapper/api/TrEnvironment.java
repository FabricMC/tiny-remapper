package net.fabricmc.tinyremapper.api;

public interface TrEnvironment {
	int getMrjVersion();
	TrRemapper getRemapper();

	/**
	 * @return the class with the passed name, or null if not found.
	 */
	TrClass getClass(String internalName);

	void propagate(TrMember member, String newName);
}
