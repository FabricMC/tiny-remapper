/*
 * Copyright (C) 2016, 2018 Player, asie
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

import java.io.*;
import java.nio.file.*;

public class TestUtil {
    public static Path folder = null;

    public static void copyFile(Class<?> cls, String path)
            throws IOException {
        if (folder == null) { throw new RuntimeException("the temporary folder is not created"); }

        try (InputStream input = cls.getResourceAsStream(path)) {
            if (input == null) { throw new IOException("input is null"); }
            byte[] buffer = new byte[input.available()];
            //noinspection ResultOfMethodCallIgnored
            input.read(buffer);

            Path target = folder.resolve(path.substring(1));
            Files.createDirectories(target.getParent());
            Files.createFile(target);

            try (OutputStream output = new FileOutputStream(target.toFile())) {
                output.write(buffer);
            }
        }
    }

    public static File getFile(String path) {
        return folder.resolve(path.substring(1)).toFile();
    }
}
