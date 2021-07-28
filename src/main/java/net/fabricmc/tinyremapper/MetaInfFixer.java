/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, FabricMC
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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class MetaInfFixer implements OutputConsumerPath.ResourceRemapper {
	public static final MetaInfFixer INSTANCE = new MetaInfFixer();

	protected MetaInfFixer() { }

	@Override
	public boolean canTransform(TinyRemapper remapper, Path relativePath) {
		return relativePath.startsWith("META-INF")
				&& (shouldStripForFixMeta(relativePath)
						|| relativePath.getFileName().toString().equals("MANIFEST.MF")
						|| (remapper != null && relativePath.getNameCount() == 3 && relativePath.getName(1).toString().equals("services")));
	}

	@Override
	public void transform(Path destinationDirectory, Path relativePath, InputStream input, TinyRemapper remapper) throws IOException {
		String fileName = relativePath.getFileName().toString();

		if (relativePath.getNameCount() == 2 && fileName.equals("MANIFEST.MF")) {
			Manifest manifest = new Manifest(input);
			fixManifest(manifest, remapper);

			Path outputFile = destinationDirectory.resolve(relativePath);
			Path outputDir = outputFile.getParent();
			if (outputDir != null) Files.createDirectories(outputDir);

			try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
				manifest.write(os);
			}
		} else if (remapper != null && relativePath.getNameCount() == 3 && relativePath.getName(1).toString().equals("services")) {
			Path outputDir = destinationDirectory.resolve(relativePath).getParent();
			Files.createDirectories(outputDir);
			Path outputFile = outputDir.resolve(mapFullyQualifiedClassName(fileName, remapper));

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(input));
					BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				fixServiceDecl(reader, writer, remapper);
			}
		}
	}

	private static boolean shouldStripForFixMeta(Path file) {
		if (file.getNameCount() != 2) return false; // not directly inside META-INF dir

		assert file.getName(0).toString().equals("META-INF");

		String fileName = file.getFileName().toString();

		// https://docs.oracle.com/en/java/javase/12/docs/specs/jar/jar.html#signed-jar-file
		return fileName.endsWith(".SF")
				|| fileName.endsWith(".DSA")
				|| fileName.endsWith(".RSA")
				|| fileName.startsWith("SIG-");
	}

	private static String mapFullyQualifiedClassName(String name, TinyRemapper tr) {
		assert name.indexOf('/') < 0;

		return tr.defaultState.remapper.map(name.replace('.', '/')).replace('/', '.');
	}

	private static void fixManifest(Manifest manifest, TinyRemapper remapper) {
		Attributes mainAttrs = manifest.getMainAttributes();

		if (remapper != null) {
			String val = mainAttrs.getValue(Attributes.Name.MAIN_CLASS);
			if (val != null) mainAttrs.put(Attributes.Name.MAIN_CLASS, mapFullyQualifiedClassName(val, remapper));

			val = mainAttrs.getValue("Launcher-Agent-Class");
			if (val != null) mainAttrs.put("Launcher-Agent-Class", mapFullyQualifiedClassName(val, remapper));
		}

		mainAttrs.remove(Attributes.Name.SIGNATURE_VERSION);

		for (Iterator<Attributes> it = manifest.getEntries().values().iterator(); it.hasNext(); ) {
			Attributes attrs = it.next();

			for (Iterator<Object> it2 = attrs.keySet().iterator(); it2.hasNext(); ) {
				Attributes.Name attrName = (Attributes.Name) it2.next();
				String name = attrName.toString();

				if (name.endsWith("-Digest") || name.contains("-Digest-") || name.equals("Magic")) {
					it2.remove();
				}
			}

			if (attrs.isEmpty()) it.remove();
		}
	}

	private static void fixServiceDecl(BufferedReader reader, BufferedWriter writer, TinyRemapper remapper) throws IOException {
		String line;

		while ((line = reader.readLine()) != null) {
			int end = line.indexOf('#');
			if (end < 0) end = line.length();

			// trim start+end to skip ' ' and '\t'

			int start = 0;
			char c;

			while (start < end && ((c = line.charAt(start)) == ' ' || c == '\t')) {
				start++;
			}

			while (end > start && ((c = line.charAt(end - 1)) == ' ' || c == '\t')) {
				end--;
			}

			if (start == end) {
				writer.write(line);
			} else {
				writer.write(line, 0, start);
				writer.write(mapFullyQualifiedClassName(line.substring(start, end), remapper));
				writer.write(line, end, line.length() - end);
			}

			writer.newLine();
		}
	}
}
