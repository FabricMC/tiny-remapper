package net.fabricmc.tinyremapper;


import java.util.function.Supplier;

public interface ResourceRemapper {

	/**
	 * @return null if no transformation occured
	 */
	byte[] transform(String fileName, Supplier<byte[]> input);
}
