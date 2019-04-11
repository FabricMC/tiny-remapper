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
import java.util.*;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;

import org.objectweb.asm.commons.Remapper;

public final class TinyUtils {
	public static class Mapping {
		public final String owner, name, desc;

		public Mapping(String owner, String name, String desc) {
			this.owner = owner;
			this.name = name;
			this.desc = desc;
		}

		@Override
		public boolean equals(Object other) {
			if (other == null || !(other instanceof Mapping)) {
				return false;
			} else {
				Mapping otherM = (Mapping) other;
				return owner.equals(otherM.owner) && name.equals(otherM.name) && Objects.equals(desc, otherM.desc);
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(owner, name, desc);
		}
	}

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

	public static IMappingProvider createTinyMappingProvider(final Path mappings, String fromM, String toM) {
		return (classMap, fieldMap, methodMap) -> {
			try (BufferedReader reader = getMappingReader(mappings.toFile())) {
				readInternal(reader, fromM, toM, classMap, fieldMap, methodMap);
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
				readInternal(reader, fromM, toM, classMap, fieldMap, methodMap);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			System.out.printf("%d classes, %d methods, %d fields%n", classMap.size(), methodMap.size(), fieldMap.size());
		};
	}

	private static void readInternal(BufferedReader reader, String fromM, String toM, Map<String, String> classMap, Map<String, String> fieldMap, Map<String, String> methodMap) throws IOException {
		TinyUtils.read(reader, fromM, toM, (classFrom, classTo) -> {
			classMap.put(classFrom, classTo);
		}, (fieldFrom, nameTo) -> {
			fieldMap.put(fieldFrom.owner + "/" + MemberInstance.getFieldId(fieldFrom.name, fieldFrom.desc), nameTo);
		}, (methodFrom, nameTo) -> {
			methodMap.put(methodFrom.owner + "/" + MemberInstance.getMethodId(methodFrom.name, methodFrom.desc), nameTo);
		});
	}

	public static void read(BufferedReader reader, String from, String to,
			BiConsumer<String, String> classMappingConsumer,
			BiConsumer<Mapping, String> fieldMappingConsumer,
			BiConsumer<Mapping, String> methodMappingConsumer)
					throws IOException {
		String[] header = reader.readLine().split("\t");
		if (header.length <= 1
				|| !header[0].equals("v1")) {
			throw new IOException("Invalid mapping version!");
		}

		List<String> headerList = Arrays.asList(header);
		int fromIndex = headerList.indexOf(from) - 1;
		int toIndex = headerList.indexOf(to) - 1;

		if (fromIndex < 0) throw new IOException("Could not find mapping '" + from + "'!");
		if (toIndex < 0) throw new IOException("Could not find mapping '" + to + "'!");

		Map<String, String> obfFrom = new HashMap<>();
		List<String[]> linesStageTwo = new ArrayList<>();

		String line;
		while ((line = reader.readLine()) != null) {
			String[] splitLine = line.split("\t");

			if (splitLine.length >= 2) {
				if ("CLASS".equals(splitLine[0])) {
					classMappingConsumer.accept(splitLine[1 + fromIndex], splitLine[1 + toIndex]);
					obfFrom.put(splitLine[1], splitLine[1 + fromIndex]);
				} else {
					linesStageTwo.add(splitLine);
				}
			}
		}

		SimpleClassMapper descObfFrom = new SimpleClassMapper(obfFrom);

		for (String[] splitLine : linesStageTwo) {
			String type = splitLine[0];
			BiConsumer<Mapping, String> consumer;

			if ("FIELD".equals(type)) {
				consumer = fieldMappingConsumer;
			} else if ("METHOD".equals(type)) {
				consumer = methodMappingConsumer;
			} else {
				continue;
			}

			String owner = obfFrom.getOrDefault(splitLine[1], splitLine[1]);
			String name = splitLine[3 + fromIndex];
			String desc = descObfFrom.mapDesc(splitLine[2]);

			Mapping mapping = new Mapping(owner, name, desc);
			String nameTo = splitLine[3 + toIndex];

			consumer.accept(mapping, nameTo);
		}
	}
}
