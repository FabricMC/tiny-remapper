package net.fabricmc.tinyremapper;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class OutputConsumerJar implements BiConsumer<String, byte[]> {
    private final FileOutputStream fos;
    private final JarOutputStream outputStream;
    private final ExecutorService jarOutputPool;

    public OutputConsumerJar(File file) throws IOException {
        this.fos = new FileOutputStream(file);
        this.outputStream = new JarOutputStream(fos);
        this.jarOutputPool = Executors.newSingleThreadExecutor();
    }

    public void addNonClassFiles(File file) {
        jarOutputPool.submit(() -> {
            try {
                FileInputStream fis = new FileInputStream(file);
                JarInputStream inputStream = new JarInputStream(fis);

                JarEntry entry;
                while ((entry = inputStream.getNextJarEntry()) != null) {
                    if (entry.getName().endsWith(".class")) continue;

                    outputStream.putNextEntry(entry);
                    ByteStreams.copy(inputStream, outputStream);
                }

                inputStream.close();
                fis.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void accept(String clsName, byte[] data) {
        String filename = clsName + ".class";
        jarOutputPool.submit(() -> {
            try {
                JarEntry entry = new JarEntry(filename);
                entry.setSize(data.length);
                entry.setLastModifiedTime(FileTime.from(Instant.now()));
                outputStream.putNextEntry(entry);
                outputStream.write(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void finish() throws IOException {
        jarOutputPool.shutdown();

        try {
            jarOutputPool.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        outputStream.close();
        fos.close();
    }
}
