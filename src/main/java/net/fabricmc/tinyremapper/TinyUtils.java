package net.fabricmc.tinyremapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
				String owner = obfFrom.get(splitLine[1]);
				String desc = descObfFrom.mapDesc(splitLine[2]);
				String tOwner = obfTo.get(splitLine[1]);
				fieldMap.put(owner + "/" + splitLine[3 + fromIndex] + ";;" + desc, tOwner + "/" + splitLine[3 + toIndex]);
			} else if ("METHOD".equals(splitLine[0])) {
				String owner = obfFrom.get(splitLine[1]);
				String desc = descObfFrom.mapMethodDesc(splitLine[2]);
				String tOwner = obfTo.get(splitLine[1]);
				methodMap.put(owner + "/" + splitLine[3 + fromIndex] + desc, tOwner + "/" + splitLine[3 + toIndex]);
			}
		}
	}
}
