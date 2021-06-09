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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IntegrationTest1 {
	private static final String MAPPING1_PATH = "/mapping/mapping1.tiny";
	private static final String BASIC_INPUT_PATH = "/integration/basic/input.jar";
	private static final String MRJ1_INPUT_PATH = "/integration/mrj1/input.jar";
	private static final String MRJ2_INPUT_PATH = "/integration/mrj2/input.jar";

	@TempDir
	static Path folder;

	@BeforeAll
	public static void setup() throws IOException {
		TestUtil.folder = folder;

		TestUtil.copyFile(IntegrationTest1.class, MAPPING1_PATH);

		TestUtil.copyFile(IntegrationTest1.class, BASIC_INPUT_PATH);
		TestUtil.copyFile(IntegrationTest1.class, MRJ1_INPUT_PATH);
		TestUtil.copyFile(IntegrationTest1.class, MRJ2_INPUT_PATH);
	}

	private TinyRemapper setupRemapper() {
		// copy from Main.java
		final boolean reverse = false;
		final boolean ignoreFieldDesc = false;
		final boolean propagatePrivate = false;
		final boolean removeFrames = false;
		final Set<String> forcePropagation = Collections.emptySet();
		final File forcePropagationFile = null;
		final boolean ignoreConflicts = false;
		final boolean checkPackageAccess = false;
		final boolean fixPackageAccess = false;
		final boolean resolveMissing = false;
		final boolean rebuildSourceFilenames = false;
		final boolean skipLocalVariableMapping = false;
		final boolean renameInvalidLocals = false;
		final int threads = -1;

		final String from = "a";
		final String to = "b";

		Path mappings = TestUtil.getFile(IntegrationTest1.MAPPING1_PATH).toPath();

		return TinyRemapper.newRemapper()
				.withMappings(TinyUtils.createTinyMappingProvider(mappings, from, to))
				.ignoreFieldDesc(ignoreFieldDesc)
				.withForcedPropagation(forcePropagation)
				.propagatePrivate(propagatePrivate)
				.removeFrames(removeFrames)
				.ignoreConflicts(ignoreConflicts)
				.checkPackageAccess(checkPackageAccess)
				.fixPackageAccess(fixPackageAccess)
				.resolveMissing(resolveMissing)
				.rebuildSourceFilenames(rebuildSourceFilenames)
				.skipLocalVariableMapping(skipLocalVariableMapping)
				.renameInvalidLocals(renameInvalidLocals)
				.threads(threads)
				.build();
	}

	/**
	 * This is a simple remap test that only remap a name of class.
	 *
	 * @throws IOException io failure.
	 */
	@Test
	public void basic() throws IOException {
		final TinyRemapper remapper = setupRemapper();
		final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;
		final Path[] classpath = new Path[]{};

		Path output = TestUtil.output(BASIC_INPUT_PATH);
		Path input = TestUtil.input(BASIC_INPUT_PATH);

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			outputConsumer.addNonClassFiles(input, ncCopyMode, remapper);

			remapper.readInputs(input);
			remapper.readClassPath(classpath);

			remapper.apply(outputConsumer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			remapper.finish();
		}

		final String MAIN_CLASS = "com/github/logicf/Main.class";
		final String GREETING_CLASS = "com/github/logicf/Greeting.class";

		JarFile result = new JarFile(output.toFile());
		assertNotNull(result.getEntry(MAIN_CLASS));
		assertNotNull(result.getEntry(GREETING_CLASS));
		result.close();
	}

	/**
	 * This is a simple remap test for a Multi-Release Jar.
	 *
	 * @throws IOException io failure.
	 */
	@Test
	public void mrj1() throws IOException {
		final TinyRemapper remapper = setupRemapper();
		final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;
		final Path[] classpath = new Path[]{};

		Path output = TestUtil.output(MRJ1_INPUT_PATH);
		Path input = TestUtil.input(MRJ1_INPUT_PATH);

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			outputConsumer.addNonClassFiles(input, ncCopyMode, remapper);

			remapper.readInputs(input);
			remapper.readClassPath(classpath);

			remapper.apply(outputConsumer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			remapper.finish();
		}

		final String MAIN_CLASS = "com/github/logicf/Main.class";
		final String GREETING_CLASS = "com/github/logicf/Greeting.class";
		final String MRJ_GREETING_CLASS = "META-INF/versions/9/com/github/logicf/Greeting.class";

		JarFile result = new JarFile(output.toFile());
		assertNotNull(result.getEntry(MAIN_CLASS));
		assertNotNull(result.getEntry(GREETING_CLASS));
		assertNotNull(result.getEntry(MRJ_GREETING_CLASS));
		result.close();
	}

	/**
	 * This tests the cross-version isolation of {@code children} and {@code parents}
	 * in the {@code ClassInstance} for Multi-Release Jar.
	 *
	 * @throws IOException io failure.
	 */
	@Test
	public void mrj2() throws IOException {
		final TinyRemapper remapper = setupRemapper();
		final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;
		final Path[] classpath = new Path[]{};

		Path output = TestUtil.output(MRJ2_INPUT_PATH);
		Path input = TestUtil.input(MRJ2_INPUT_PATH);

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			outputConsumer.addNonClassFiles(input, ncCopyMode, remapper);

			remapper.readInputs(input);
			remapper.readClassPath(classpath);

			remapper.apply(outputConsumer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			remapper.finish();
		}

		final String J8_MAIN_CLASS = "com/github/logicf/Main.class";
		final String J8_D1_CLASS = "com/github/logicf/D1.class";
		final String J8_D3_CLASS = "com/github/logicf/D3.class";
		final String J8_D5_CLASS = "com/github/logicf/D5.class";
		final String J9_D1_CLASS = "META-INF/versions/9/com/github/logicf/D1.class";
		final String J9_D2_CLASS = "META-INF/versions/9/com/github/logicf/D2.class";
		final String J9_D4_CLASS = "META-INF/versions/9/com/github/logicf/D4.class";

		JarFile result = new JarFile(output.toFile());

		assertNotNull(result.getEntry(J8_MAIN_CLASS));
		assertNotNull(result.getEntry(J8_D1_CLASS));
		assertNotNull(result.getEntry(J8_D3_CLASS));
		assertNotNull(result.getEntry(J8_D5_CLASS));
		assertNotNull(result.getEntry(J9_D1_CLASS));
		assertNotNull(result.getEntry(J9_D2_CLASS));
		assertNotNull(result.getEntry(J9_D4_CLASS));

		ClassReader readerJ8D1 = new ClassReader(result.getInputStream(result.getEntry(J8_D1_CLASS)));
		readerJ8D1.accept(new ClassVisitor(Opcodes.ASM9, null) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				assertEquals("com/github/logicf/D3", superName);
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

		ClassReader readerJ9D1 = new ClassReader(result.getInputStream(result.getEntry(J9_D1_CLASS)));
		readerJ9D1.accept(new ClassVisitor(Opcodes.ASM9, null) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				assertEquals("com/github/logicf/D2", superName);
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

		result.close();
	}

	/**
	 * This tests the Multi-Release Jar with input tag.
	 *
	 * @throws IOException io failure.
	 */
	@Test
	public void mrj4() throws IOException {
		final TinyRemapper remapper = setupRemapper();
		final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;
		final Path[] classpath = new Path[]{};
		InputTag tag = remapper.createInputTag();

		Path output = TestUtil.output(MRJ1_INPUT_PATH);
		Path input = TestUtil.input(MRJ1_INPUT_PATH);

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			outputConsumer.addNonClassFiles(input, ncCopyMode, remapper);

			remapper.readInputs(tag, input);
			remapper.readClassPath(classpath);

			remapper.apply(outputConsumer, tag);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			remapper.finish();
		}

		final String MAIN_CLASS = "com/github/logicf/Main.class";
		final String GREETING_CLASS = "com/github/logicf/Greeting.class";
		final String MRJ_GREETING_CLASS = "META-INF/versions/9/com/github/logicf/Greeting.class";

		JarFile result = new JarFile(output.toFile());
		assertNotNull(result.getEntry(MAIN_CLASS));
		assertNotNull(result.getEntry(GREETING_CLASS));
		assertNotNull(result.getEntry(MRJ_GREETING_CLASS));
		result.close();
	}

	@AfterAll
	public static void cleanup() throws IOException {
		TestUtil.folder = null;
	}
}