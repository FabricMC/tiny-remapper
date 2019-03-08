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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import net.fabricmc.mappings.*;
import org.objectweb.asm.commons.Remapper;

public final class TinyUtils {
	private interface InputStreamConsumer {
		void apply(InputStream stream) throws IOException;
	}

	private TinyUtils() {

	}

	static IMappingProvider createTinyMappingProvider(final Path mappingsPath, String fromM, String toM) {
		return (classMap, fieldMap, methodMap) -> {
			try {
				withMapping(mappingsPath, (stream) -> {
					Mappings mappings = MappingsProvider.readTinyMappings(stream);
					MappingProviderUtils.create(mappings, fromM, toM).load(classMap, fieldMap, methodMap);
				});
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			System.out.printf("%s: %d classes, %d methods, %d fields%n", mappingsPath.toFile().getName(), classMap.size(), methodMap.size(), fieldMap.size());
		};
	}

	private static void withMapping(Path path, InputStreamConsumer consumer) throws IOException {
		File file = path.toFile();
		try (
				InputStream is = new FileInputStream(file);
				InputStream targetIs = file.getName().endsWith(".gz") ? new GZIPInputStream(is) : is
		) {
			consumer.apply(targetIs);
		}
	}
}
