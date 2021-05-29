package net.fabricmc.tinyremapper;

import java.io.InputStream;
import java.nio.file.Path;

public class MetaInfRemover implements OutputConsumerPath.ResourceRemapper {
	public static final MetaInfRemover INSTANCE = new MetaInfRemover();

	protected MetaInfRemover() {}

	@Override
	public boolean canTransform(TinyRemapper remapper, Path relativePath) {
		return relativePath.startsWith("META-INF") && relativePath.getNameCount() != 2;
	}

	@Override
	public void transform(Path destinationDirectory, Path relativePath, InputStream input, TinyRemapper remapper) {
	}
}
