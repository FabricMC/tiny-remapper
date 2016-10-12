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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;

import org.objectweb.asm.commons.Remapper;

public final class TinyUtils {
	private static class SimpleClassMapper extends Remapper {
		final Map<String, String> classMap;

		public SimpleClassMapper(Map<String, String> map) {
			this.classMap = map;
		}

		@Override
		public String map(String typeName) {
			return classMap.getOrDefault(typeName, typeName);
		}
	}

	private TinyUtils() {

	}

	private static final String[] removeFirst(String[] src, int count) {
		if (count >= src.length) {
			return new String[0];
		} else {
			String[] out = new String[src.length - count];
			System.arraycopy(src, count, out, 0, out.length);
			return out;
		}
	}

	public static IMappingProvider createTinyMappingProvider(final Path mappings, String fromM, String toM) {
		return (classMap, fieldMap, methodMap) -> {
			try (BufferedReader reader = getMappingReader(mappings.toFile())) {
				TinyUtils.read(reader, fromM, toM, classMap, fieldMap, methodMap);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			System.out.printf("%s: %d classes, %d methods, %d fields%n", mappings.getFileName().toString(), classMap.size(), methodMap.size(), fieldMap.size());
		};
	}

	private static BufferedReader getMappingReader(File file) throws IOException {
		InputStream is = new FileInputStream(file);

		if (file.getName().endsWith(".gz")) {
			is = new GZIPInputStream(is);
		}

		return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
	}

	public static IMappingProvider createTinyMappingProvider(final BufferedReader reader, String fromM, String toM) {
		return (classMap, fieldMap, methodMap) -> {
			try {
				TinyUtils.read(reader, fromM, toM, classMap, fieldMap, methodMap);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			System.out.printf("%d classes, %d methods, %d fields%n", classMap.size(), methodMap.size(), fieldMap.size());
		};
	}

	public static BiConsumer<String, byte[]> createOutputConsumerDir(final Path outputPath) {
		return (clsName, data) -> {
		};
	}

	public static void read(BufferedReader reader, String from, String to, Map<String, String> classMap, Map<String, String> fieldMap, Map<String, String> methodMap)
			throws IOException {
		String[] header = reader.readLine().split("\t");
		if (header.length <= 1
				|| !header[0].equals("v1")) {
			throw new IOException("Invalid mapping version!");
		}

		List<String> headerList = Arrays.asList(removeFirst(header, 1));
		int fromIndex = headerList.indexOf(from);
		int toIndex = headerList.indexOf(to);

		if (fromIndex < 0) throw new IOException("Could not find mapping '" + from + "'!");
		if (toIndex < 0) throw new IOException("Could not find mapping '" + to + "'!");

		Map<String, String> obfFrom = new HashMap<>();
		Map<String, String> obfTo = new HashMap<>();
		List<String[]> linesStageTwo = new ArrayList<>();

		String line;
		while ((line = reader.readLine()) != null) {
			String[] splitLine = line.split("\t");
			if (splitLine.length >= 2) {
				if ("CLASS".equals(splitLine[0])) {
					classMap.put(splitLine[1 + fromIndex], splitLine[1 + toIndex]);
					obfFrom.put(splitLine[1], splitLine[1 + fromIndex]);
					obfTo.put(splitLine[1], splitLine[1 + toIndex]);
				} else {
					linesStageTwo.add(splitLine);
				}
			}
		}

		SimpleClassMapper descObfFrom = new SimpleClassMapper(obfFrom);

		for (String[] splitLine : linesStageTwo) {
			if ("FIELD".equals(splitLine[0])) {
				String owner = obfFrom.getOrDefault(splitLine[1], splitLine[1]);
				String desc = descObfFrom.mapDesc(splitLine[2]);
				String tOwner = obfTo.getOrDefault(splitLine[1], splitLine[1]);
				fieldMap.put(owner + "/" + splitLine[3 + fromIndex] + ";;" + desc, tOwner + "/" + splitLine[3 + toIndex]);
			} else if ("METHOD".equals(splitLine[0])) {
				String owner = obfFrom.getOrDefault(splitLine[1], splitLine[1]);
				String desc = descObfFrom.mapMethodDesc(splitLine[2]);
				String tOwner = obfTo.getOrDefault(splitLine[1], splitLine[1]);
				methodMap.put(owner + "/" + splitLine[3 + fromIndex] + desc, tOwner + "/" + splitLine[3 + toIndex]);
			}
		}
	}
}
