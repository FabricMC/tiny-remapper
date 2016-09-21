/*
 * Copyright (C) 2016 Player, asie
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
