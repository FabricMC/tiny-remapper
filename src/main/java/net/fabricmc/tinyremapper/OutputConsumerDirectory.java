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
