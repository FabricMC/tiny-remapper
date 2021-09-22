package net.fabricmc.tinyremapper.api.io;

import net.fabricmc.tinyremapper.api.TrClass;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @deprecated Unstable API
 * Only {@link OutputConsumer#acceptResource(Path, byte[])}
 * and {@link OutputConsumer#acceptClassFile(String, int, byte[])}
 * will be called in the internal implementation.
 */
public interface OutputConsumer {
    void acceptResource(Path path, byte[] data) throws IOException;
    void acceptClassFile(Path path, byte[] data) throws IOException;
    default void acceptClassFile(String name, int mrjVersion, byte[] data) throws IOException {
        acceptClassFile(TrClass.getPathInJar(name, mrjVersion), data);
    }
}
