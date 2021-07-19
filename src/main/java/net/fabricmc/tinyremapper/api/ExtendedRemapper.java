package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.commons.Remapper;

public abstract class ExtendedRemapper extends Remapper {
	/**
	 * remaps a method when the full descriptor is unknown.
	 * @param owner the owner of the method
	 * @param name the name of the method
	 * @param descPrefix the part of the descriptor that is known (must be the start)
	 * @return the mapped name
	 */
	public abstract String mapMethodNamePrefixDesc(String owner, String name, String descPrefix);

	/**
	 * remaps a parameter name.
	 * @param lvIndex the local variable index of the arg
	 */
	public abstract String mapMethodArg(String methodOwner, String methodName, String methodDesc, int lvIndex, String name);
}
