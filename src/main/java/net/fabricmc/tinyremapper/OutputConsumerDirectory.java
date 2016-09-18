package net.fabricmc.tinyremapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public class OutputConsumerDirectory implements BiConsumer<String, byte[]> {
    private final Path outputPath;

    public OutputConsumerDirectory(Path outputPath) {
        this.outputPath = outputPath;
    }

    @Override
    public void accept(String clsName, byte[] data) {
        int pkgEnd = clsName.lastIndexOf('/');
        Path outputPath1 = outputPath;

        if (pkgEnd != -1) outputPath1 = outputPath1.resolve(clsName.substring(0, pkgEnd));
        Path output = outputPath1.resolve(clsName.substring(pkgEnd + 1)+".class");

        try {
            Files.createDirectories(outputPath1);
            Files.write(output, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
