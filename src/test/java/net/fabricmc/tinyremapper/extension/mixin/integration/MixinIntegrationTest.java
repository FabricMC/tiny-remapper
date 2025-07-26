/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2025, FabricMC
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

package net.fabricmc.tinyremapper.extension.mixin.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import net.fabricmc.tinyremapper.extension.mixin.integration.mixins.TargetMixin;
import net.fabricmc.tinyremapper.extension.mixin.integration.targets.Target;

public class MixinIntegrationTest {
	@TempDir
	static Path folder;

	@Test
	public void remapWildcardName() throws IOException {
		Path classpath = createJar(Target.class);
		Path input = createJar(TargetMixin.class);
		Path output = folder.resolve("output.jar");

		TinyRemapper tinyRemapper = TinyRemapper.newRemapper()
				.extension(new MixinExtension())
				.withMappings(out -> out.acceptClass("java/lang/String", "com/example/NotString"))
				.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			tinyRemapper.readClassPath(classpath);
			tinyRemapper.readInputs(input);

			tinyRemapper.apply(outputConsumer);
		}

		String remapped = textify(output, TargetMixin.class);
		// Check constructor inject did not gain a desc
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"<init>*\"}"));
		// Check that wildcard desc is remapped without a name
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"*()Lcom/example/NotString;\"}"));
	}

	// Create a zip file in the temp dir containing only the passed class file.
	private Path createJar(Class<?> clazz) throws IOException {
		String classFileName = clazz.getName().replace('.', '/') + ".class";
		Path jarFile = folder.resolve(clazz.getSimpleName() + ".jar");

		try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarFile))) {
			jarOut.putNextEntry(new JarEntry(classFileName));

			try (InputStream classIn = clazz.getResourceAsStream('/' + classFileName)) {
				byte[] buffer = new byte[8192];
				int bytesRead;

				while ((bytesRead = classIn.read(buffer)) != -1) {
					jarOut.write(buffer, 0, bytesRead);
				}
			}

			jarOut.closeEntry();
		}

		return jarFile;
	}

	public static String textify(Path zipPath, Class<?> clazz) throws IOException {
		String classFileName = clazz.getName().replace('.', '/') + ".class";

		try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
			ZipEntry entry = zipFile.getEntry(classFileName);

			try (InputStream inputStream = zipFile.getInputStream(entry)) {
				ClassReader classReader = new ClassReader(inputStream);
				StringWriter stringWriter = new StringWriter();
				PrintWriter printWriter = new PrintWriter(stringWriter);
				TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, new Textifier(), printWriter);

				classReader.accept(traceClassVisitor, 0);
				return stringWriter.toString();
			}
		}
	}
}
