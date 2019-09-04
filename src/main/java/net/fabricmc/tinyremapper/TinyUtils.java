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
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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

import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import net.fabricmc.tinyremapper.IMappingProvider.Member;

public final class TinyUtils {
	private static final class MemberMapping {
		public MemberMapping(Member member, String newName) {
			this.member = member;
			this.newName = newName;
		}

		public Member member;
		public String newName;
	}

	private static final class MethodArgMapping {
		public MethodArgMapping(Member method, int lvIndex, String newName) {
			this.method = method;
			this.lvIndex = lvIndex;
			this.newName = newName;
		}

		public Member method;
		public int lvIndex;
		public String newName;
	}

	private static final class MethodVarMapping {
		public MethodVarMapping(Member method, int lvIndex, int startOpIdx, int asmIndex, String newName) {
			this.method = method;
			this.lvIndex = lvIndex;
			this.startOpIdx = startOpIdx;
			this.asmIndex = asmIndex;
			this.newName = newName;
		}

		public Member method;
		public int lvIndex, startOpIdx, asmIndex;
		public String newName;
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
		return out -> {
			try (BufferedReader reader = getMappingReader(mappings.toFile())) {
				readInternal(reader, fromM, toM, out);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			//System.out.printf("%s: %d classes, %d methods, %d fields%n", mappings.getFileName().toString(), classMap.size(), methodMap.size(), fieldMap.size());
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
		return out -> {
			try {
				readInternal(reader, fromM, toM, out);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			//System.out.printf("%d classes, %d methods, %d fields%n", classMap.size(), methodMap.size(), fieldMap.size());
		};
	}

	public static IMappingProvider createProguardOutputMappingProvider(final Path mappings) {
		return (classMap, fieldMap, methodMap) -> {
			try (BufferedReader reader = getMappingReader(mappings.toFile())) {
				readProguard(reader, classMap, fieldMap, methodMap);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			System.out.printf("%s: %d classes, %d methods, %d fields%n", mappings.getFileName().toString(), classMap.size(), methodMap.size(),
					fieldMap.size());
		};
	}

	public static IMappingProvider createProguardOutputMappingProvider(final BufferedReader reader) {
		return (classMap, fieldMap, methodMap) -> {
			try {
				readProguard(reader, classMap, fieldMap, methodMap);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			System.out.printf("%d classes, %d methods, %d fields%n", classMap.size(), methodMap.size(), fieldMap.size());
		};
	}

	private static void readProguard(final BufferedReader reader, Map<String, String> classMap, Map<String, String> fieldMap,
									 Map<String, String> methodMap) throws IOException {
		Map<String, String> inversedClassMap = new HashMap<>();
		List<Mapping> fieldMappings = new ArrayList<>();
		List<Mapping> methodMappings = new ArrayList<>();

		String line;
		String inClass = null;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("#")) {
				continue; // Ignore comments
			}
			if (line.startsWith(PROGUARD_MEMBER_INDENT)) {
				String[] parts = line.substring(PROGUARD_MEMBER_INDENT.length()).split(" -> ");
				if (parts.length != 2 || inClass == null) {
					throw new IOException("Invalid member line " + line);
				}
				System.out.println("Member \"" + parts[0] + "\" <- \"" + parts[1] + "\"");

				String[] descAndName = parts[0].split(" ");
				if (descAndName.length != 2) {
					throw new IOException("Invalid member line " + line);
				}

				boolean method = descAndName[0].indexOf(':') != -1;
				if (method) {
					descAndName[0] = fixProguardDescriptors(descAndName[1].substring(descAndName[1].indexOf('('))) + fixProguardType(
							descAndName[0].substring(descAndName[0].lastIndexOf(':') + 1));
					descAndName[1] = descAndName[1].substring(0, descAndName[1].indexOf('('));
					methodMappings.add(new Mapping(inClass, parts[1], descAndName[0], descAndName[1]));
				} else {
					descAndName[0] = fixProguardType(descAndName[0]);
					fieldMappings.add(new Mapping(inClass, parts[1], descAndName[0], descAndName[1]));
				}
			} else {
				String[] parts = line.split(" -> ");
				if (parts.length != 2 || !parts[1].endsWith(":")) {
					throw new IOException("Invalid class line " + line);
				}
				parts[1] = parts[1].substring(0, parts[1].length() - 1);
				System.out.println("Class \"" + parts[0] + "\" <- \"" + parts[1] + "\"");
				inClass = parts[1].replace('.', '/');
				String outClass = parts[0].replace('.', '/');
				classMap.put(inClass, outClass);
				inversedClassMap.put(outClass, inClass);
			}
		}

		SimpleClassMapper mapper = new SimpleClassMapper(inversedClassMap);
		for (Mapping m : methodMappings) {
			m.desc = mapper.mapDesc(m.desc);
			methodMap.put(m.owner + "/" + MemberInstance.getMethodId(m.name, m.desc), m.newName);
		}

		for (Mapping m : fieldMappings) {
			m.desc = mapper.mapDesc(m.desc);
			fieldMap.put(m.owner + "/" + MemberInstance.getFieldId(m.name, m.desc), m.newName);
		}
	}

	private static String fixProguardDescriptors(String param) {
		if (param.length() == 2)
			return param;
		String[] parts = param.substring(1, param.length() - 1).split(",");
		StringBuilder sb = new StringBuilder();
		sb.append('(');
		for (String part : parts) {
			sb.append(fixProguardType(part));
		}
		sb.append(')');
		return sb.toString();
	}

	private static String fixProguardType(String proguardType) {
		int arrayLevel = 0;
		int len = proguardType.length();
		while (len - 2 > arrayLevel * 2 && proguardType.charAt(len - arrayLevel * 2 - 1) == ']' && proguardType.charAt(len - arrayLevel * 2 - 2) == '[') {
			arrayLevel++;
		}

		String body;
		String check = arrayLevel == 0 ? proguardType : proguardType.substring(0, len - arrayLevel * 2);
		switch (check) {
			case "byte":
				body = "B";
				break;
			case "char":
				body = "C";
				break;
			case "double":
				body = "D";
				break;
			case "float":
				body = "F";
				break;
			case "int":
				body = "I";
				break;
			case "long":
				body = "J";
				break;
			case "short":
				body = "S";
				break;
			case "boolean":
				body = "Z";
				break;
			case "void":
				body = "V";
				break;
			default:
				body = "L" + check.replace('.', '/') + ";";
				break;
		}
		if (arrayLevel == 0) {
			return body;
		}

		StringBuilder sb = new StringBuilder(arrayLevel + body.length());
		for (int i = 0; i < arrayLevel; i++) {
			sb.append('[');
		}
		sb.append(body);
		return sb.toString();
	}

	private static void readInternal(BufferedReader reader, String fromM, String toM, Map<String, String> classMap, Map<String, String> fieldMap,
									 Map<String, String> methodMap) throws IOException {
		List<Mapping> fieldMappings = new ArrayList<>();
		List<Mapping> methodMappings = new ArrayList<>();

		TinyUtils.read(reader, fromM, toM,
				tmp,
				(remapClasses, classMapper) -> {
					boolean fixOwner = remapClasses;

					for (int i = 0; i < 2; i++) {
						List<Mapping> mappings = i == 0 ? fieldMappings : methodMappings;

						for (Mapping m : mappings) {
							if (fixOwner) {
								m.owner = classMapper.map(m.owner);
							}
							m.desc = classMapper.mapDesc(m.desc);
						}
					}
				});

		for (MemberMapping m : methodMappings) {
			out.acceptMethod(m.member, m.newName);
		}

		for (MethodArgMapping m : methodArgMappings) {
			out.acceptMethodArg(m.method, m.lvIndex, m.newName);
		}

		for (MethodVarMapping m : methodVarMappings) {
			out.acceptMethodVar(m.method, m.lvIndex, m.startOpIdx, m.asmIndex, m.newName);
		}

		for (MemberMapping m : fieldMappings) {
			out.acceptField(m.member, m.newName);
		}
	}

	public static void read(BufferedReader reader, String from, String to,
			MappingAcceptor out,
			BiConsumer<Boolean, SimpleClassMapper> postProcessor)
					throws IOException {
		String headerLine = reader.readLine();

		if (headerLine == null) {
			throw new EOFException();
		} else if (headerLine.startsWith("v1\t")) {
			readV1(reader, from, to, headerLine, out, postProcessor);
		} else if (headerLine.startsWith("tiny\t2\t")) {
			readV2(reader, from, to, headerLine, out, postProcessor);
		} else {
			throw new IOException("Invalid mapping version!");
		}
	}

	private static void readV1(BufferedReader reader, String from, String to,
			String headerLine,
			MappingAcceptor out,
			BiConsumer<Boolean, SimpleClassMapper> postProcessor)
					throws IOException {
		List<String> headerList = Arrays.asList(headerLine.split("\t"));
		int fromIndex = headerList.indexOf(from) - 1;
		int toIndex = headerList.indexOf(to) - 1;

		if (fromIndex < 0) {
			throw new IOException("Could not find mapping '" + from + "'!");
		}
		if (toIndex < 0) {
			throw new IOException("Could not find mapping '" + to + "'!");
		}

		Map<String, String> obfFrom = fromIndex != 0 ? new HashMap<>() : null;

		String line;
		while ((line = reader.readLine()) != null) {
			String[] splitLine = line.split("\t");
			if (splitLine.length < 2) {
				continue;
			}

			String type = splitLine[0];

			if ("CLASS".equals(type)) {
				out.acceptClass(splitLine[1 + fromIndex], splitLine[1 + toIndex]);
				if (obfFrom != null) obfFrom.put(splitLine[1], splitLine[1 + fromIndex]);
			} else {
				boolean isMethod;

				if ("FIELD".equals(type)) {
					isMethod = false;
				} else if ("METHOD".equals(type)) {
					isMethod = true;
				} else {
					continue;
				}

				String owner = splitLine[1];
				String name = splitLine[3 + fromIndex];
				String desc = splitLine[2];
				String nameTo = splitLine[3 + toIndex];
				Member member = new Member(owner, name, desc);

				if (isMethod) {
					out.acceptMethod(member, nameTo);
				} else {
					out.acceptField(member, nameTo);
				}
			}
		}

		if (obfFrom != null) {
			postProcessor.accept(true, new SimpleClassMapper(obfFrom));
		}
	}

	private static void readV2(BufferedReader reader, String from, String to,
			String headerLine,
			MappingAcceptor out,
			BiConsumer<Boolean, SimpleClassMapper> postProcessor)
					throws IOException {
		String[] parts;

		if (!headerLine.startsWith("tiny\t2\t")
				|| (parts = splitAtTab(headerLine, 0, 5)).length < 5) { //min. tiny + major version + minor version + 2 name spaces
			throw new IOException("invalid/unsupported tiny file (incorrect header)");
		}

		List<String> namespaces = Arrays.asList(parts).subList(3, parts.length);
		int nsA = namespaces.indexOf(from);
		int nsB = namespaces.indexOf(to);
		Map<String, String> obfFrom = nsA != 0 ? new HashMap<>() : null;
		int partCountHint = 2 + namespaces.size(); // suitable for members, which should be the majority
		int lineNumber = 1;

		boolean inHeader = true;
		boolean inClass = false;
		boolean inMethod = false;

		boolean escapedNames = false;

		String className = null;
		Member member = null;
		int varLvIndex = 0;
		int varStartOpIdx = 0;
		int varLvtIndex = 0;
		String line;

		while ((line = reader.readLine()) != null) {
			lineNumber++;
			if (line.isEmpty()) {
				continue;
			}

			int indent = 0;

			while (indent < line.length() && line.charAt(indent) == '\t') {
				indent++;
			}

			parts = splitAtTab(line, indent, partCountHint);
			String section = parts[0];

			if (indent == 0) {
				inHeader = inClass = inMethod = false;

				if (section.equals("c")) { // class: c <names>...
					if (parts.length != namespaces.size() + 1) {
						throw new IOException("invalid class decl in line " + lineNumber);
					}

					className = unescapeOpt(parts[1 + nsA], escapedNames);
					String mappedName = unescapeOpt(parts[1 + nsB], escapedNames);

					if (!mappedName.isEmpty()) {
						out.acceptClass(className, mappedName);
						if (obfFrom != null) obfFrom.put(unescapeOpt(parts[1], escapedNames), mappedName);
					}

					inClass = true;
				}
			} else if (indent == 1) {
				inMethod = false;

				if (inHeader) { // header k/v
					if (section.equals("escaped-names")) {
						escapedNames = true;
					}
				} else if (inClass && (section.equals("m") || section.equals("f"))) { // method/field: m/f <descA> <names>...
					boolean isMethod = section.equals("m");
					if (parts.length != namespaces.size() + 2) throw new IOException("invalid "+(isMethod ? "method" : "field")+" decl in line "+lineNumber);

					String memberDesc = unescapeOpt(parts[1], escapedNames);
					String memberName = unescapeOpt(parts[2 + nsA], escapedNames);
					String mappedName = unescapeOpt(parts[2 + nsB], escapedNames);
					member = new Member(className, memberName, memberDesc);
					inMethod = isMethod;

					if (!mappedName.isEmpty()) {
						if (isMethod) {
							out.acceptMethod(member, mappedName);
						} else {
							out.acceptField(member, mappedName);
						}
					}
				}
			} else if (indent == 2) {
				if (inMethod && section.equals("p")) { // method parameter: p <lv-index> <names>...
					if (parts.length != namespaces.size() + 2) {
						throw new IOException("invalid method parameter decl in line " + lineNumber);
					}

					varLvIndex = Integer.parseInt(parts[1]);
					String mappedName = unescapeOpt(parts[2 + nsB], escapedNames);
					if (!mappedName.isEmpty()) out.acceptMethodArg(member, varLvIndex, mappedName);
				} else if (inMethod && section.equals("v")) { // method variable: v <lv-index> <lv-start-offset> <optional-lvt-index> <names>...
					if (parts.length != namespaces.size() + 4) {
						throw new IOException("invalid method variable decl in line " + lineNumber);
					}

					varLvIndex = Integer.parseInt(parts[1]);
					varStartOpIdx = Integer.parseInt(parts[2]);
					varLvtIndex = Integer.parseInt(parts[3]);
					String mappedName = unescapeOpt(parts[4 + nsB], escapedNames);
					if (!mappedName.isEmpty()) out.acceptMethodVar(member, varLvIndex, varStartOpIdx, varLvtIndex, mappedName);
				}
			}
		}

		if (obfFrom != null) {
			postProcessor.accept(false, new SimpleClassMapper(obfFrom));
		}
	}

	private static String[] splitAtTab(String s, int offset, int partCountHint) {
		String[] ret = new String[Math.max(1, partCountHint)];
		int partCount = 0;
		int pos;

		while ((pos = s.indexOf('\t', offset)) >= 0) {
			if (partCount == ret.length) {
				ret = Arrays.copyOf(ret, ret.length * 2);
			}
			ret[partCount++] = s.substring(offset, pos);
			offset = pos + 1;
		}

		if (partCount == ret.length) {
			ret = Arrays.copyOf(ret, ret.length + 1);
		}
		ret[partCount++] = s.substring(offset);

		return partCount == ret.length ? ret : Arrays.copyOf(ret, partCount);
	}

	private static String unescapeOpt(String str, boolean escapedNames) {
		return escapedNames ? unescape(str) : str;
	}

	private static String unescape(String str) {
		int pos = str.indexOf('\\');
		if (pos < 0) {
			return str;
		}

		StringBuilder ret = new StringBuilder(str.length() - 1);
		int start = 0;

		do {
			ret.append(str, start, pos);
			pos++;
			int type;

			if (pos >= str.length()) {
				throw new RuntimeException("incomplete escape sequence at the end");
			} else if ((type = escaped.indexOf(str.charAt(pos))) < 0) {
				throw new RuntimeException("invalid escape character: \\" + str.charAt(pos));
			} else {
				ret.append(toEscape.charAt(type));
			}

			start = pos + 1;
		} while ((pos = str.indexOf('\\', start)) >= 0);

		ret.append(str, start, str.length());

		return ret.toString();
	}

	private static final String toEscape = "\\\n\r\0\t";
	private static final String escaped = "\\nr0t";
	private static final String PROGUARD_MEMBER_INDENT = "    ";
}
