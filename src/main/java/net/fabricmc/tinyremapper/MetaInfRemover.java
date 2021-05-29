package net.fabricmc.tinyremapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import net.fabricmc.tinyremapper.util.Lazy;

public class MetaInfRemover implements ResourceRemapper {
	public static final MetaInfRemover INSTANCE = new MetaInfRemover();

	protected MetaInfRemover() {}

	@Override
	public boolean canTransform(TinyRemapper remapper, Path relativePath) {
		return relativePath.startsWith("META-INF") && relativePath.getNameCount() != 2;
	}

	@Override
	public void transform(Path root, Path relativePath, InputStream input, Lazy<OutputStream> output, TinyRemapper remapper) {
	}
}
