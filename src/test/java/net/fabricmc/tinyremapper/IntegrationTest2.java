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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IntegrationTest2 {
	private static final String MAPPING2_PATH = "/mapping/mapping2.tiny";
	private static final String ACCESS_INPUT_PATH = "/integration/access/input.jar";
	private static final String MRJ3_INPUT_PATH = "/integration/mrj3/input.jar";

	@TempDir
	static Path folder;

	@BeforeAll
	public static void setup() throws IOException {
		TestUtil.folder = folder;

		TestUtil.copyFile(IntegrationTest2.class, MAPPING2_PATH);

		TestUtil.copyFile(IntegrationTest2.class, ACCESS_INPUT_PATH);
		TestUtil.copyFile(IntegrationTest2.class, MRJ3_INPUT_PATH);
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
		final boolean checkPackageAccess = true;
		final boolean fixPackageAccess = true;
		final boolean resolveMissing = false;
		final boolean rebuildSourceFilenames = false;
		final boolean skipLocalVariableMapping = false;
		final boolean renameInvalidLocals = false;
		final int threads = -1;

		final String from = "a";
		final String to = "b";

		Path mappings = TestUtil.getFile(IntegrationTest2.MAPPING2_PATH).toPath();

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
	 * This is a test for package access fix
	 * @throws IOException io failure.
	 */
	@Test
	public void access() throws IOException {
		final TinyRemapper remapper = setupRemapper();
		final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;
		final Path[] classpath = new Path[]{};

		Path output = TestUtil.output(ACCESS_INPUT_PATH);
		Path input = TestUtil.input(ACCESS_INPUT_PATH);

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
		final String D1_CLASS = "com/github/logicf/pkg1/D1.class";
		final String D3_CLASS = "com/github/logicf/pkg2/D3.class";

		JarFile result = new JarFile(output.toFile());

		assertNotNull(result.getEntry(MAIN_CLASS));
		assertNotNull(result.getEntry(D1_CLASS));
		assertNotNull(result.getEntry(D3_CLASS));

		ClassReader readerD3 = new ClassReader(result.getInputStream(result.getEntry(D3_CLASS)));

		readerD3.accept(new ClassVisitor(Opcodes.ASM9, null) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				assertNotEquals(0, (access & Opcodes.ACC_PUBLIC));
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				if (name.equals("say")) {
					assertNotEquals(0, (access & Opcodes.ACC_PUBLIC));
				}

				return super.visitMethod(access, name, desc, signature, exceptions);
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

		result.close();
	}

	/**
	 * This tests package access fix on the Multi-Release Jar.
	 * @throws IOException io failure.
	 */
	@Test
	public void mrj3() throws IOException {
		final TinyRemapper remapper = setupRemapper();
		final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;
		final Path[] classpath = new Path[]{};

		Path output = TestUtil.output(MRJ3_INPUT_PATH);
		Path input = TestUtil.input(MRJ3_INPUT_PATH);

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
		final String J8_D1_CLASS = "com/github/logicf/pkg1/D1.class";
		final String J8_D3_CLASS = "com/github/logicf/pkg2/D3.class";
		final String J9_D1_CLASS = "META-INF/versions/9/com/github/logicf/pkg1/D1.class";

		JarFile result = new JarFile(output.toFile());

		assertNotNull(result.getEntry(J8_MAIN_CLASS));
		assertNotNull(result.getEntry(J8_D1_CLASS));
		assertNotNull(result.getEntry(J8_D3_CLASS));
		assertNotNull(result.getEntry(J9_D1_CLASS));

		ClassReader readerD3 = new ClassReader(result.getInputStream(result.getEntry(J8_D3_CLASS)));

		readerD3.accept(new ClassVisitor(Opcodes.ASM9, null) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				assertNotEquals(0, (access & Opcodes.ACC_PUBLIC));
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				if (name.equals("say")) {
					assertNotEquals(0, (access & Opcodes.ACC_PUBLIC));
				}

				return super.visitMethod(access, name, desc, signature, exceptions);
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

		result.close();
	}

	@AfterAll
	public static void cleanup() throws IOException {
		TestUtil.folder = null;
	}
}