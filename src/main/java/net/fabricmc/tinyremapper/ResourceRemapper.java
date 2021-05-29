package net.fabricmc.tinyremapper;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import net.fabricmc.tinyremapper.util.Lazy;

public interface ResourceRemapper {
	boolean canTransform(TinyRemapper remapper, Path relativePath);

	/**
	 * @param output {@link Lazy#hasEvaluated()} returns false, the file is deleted (not copied to output). If it returns true, it is expected the returned output stream was closed
	 */
	void transform(Path root, Path relativePath, InputStream input, Lazy<OutputStream> output, TinyRemapper remapper) throws IOException;
}
