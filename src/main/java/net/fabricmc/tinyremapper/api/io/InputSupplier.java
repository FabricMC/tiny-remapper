package net.fabricmc.tinyremapper.api.io;

import net.fabricmc.tinyremapper.api.TrClass;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

public interface InputSupplier {
    /**
     * Get the source of the input. Only useful for logging.
     */
    String getSource();

    /**
     * Must be called exactly once before {@link InputSupplier#load(InputConsumer)}.
     */
    void open() throws IOException;

    /**
     * Load files into {@link InputConsumer}. Only valid after {@link InputSupplier#open()}, before {@link InputSupplier#close()}.
     */
    void load(InputConsumer consumer) throws IOException;

    /**
     * Release any resources bind with this object.
     */
    void close() throws IOException;

    @FunctionalInterface
    interface DataSupplier {
        byte[] get() throws IOException;
    }

    /**
     * Only {@link InputConsumer#acceptResourceFile(Path, DataSupplier)}
     * and {@link InputConsumer#acceptClassFile(Path, DataSupplier)}
     * will be overwritten in the internal implementation.
     */
    interface InputConsumer {
        /**
         * @deprecated Unstable API
         */
        void acceptResourceFile(Path path, DataSupplier data);
        void acceptClassFile(Path path, DataSupplier data);
        default void acceptClassFile(String name, int mrjVersion, DataSupplier data) {
            acceptClassFile(TrClass.getPathInJar(name, mrjVersion), data);
        }
    }
}
