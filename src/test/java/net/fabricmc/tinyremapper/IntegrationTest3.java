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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.jar.JarFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IntegrationTest3 {
	private static final String MAPPING3_PATH = "/mapping/mapping3.tiny";
	private static final String ANNOTATION_INPUT_PATH = "/integration/annotation/input.jar";

	@TempDir
	static Path folder;

	@BeforeAll
	public static void setup() throws IOException {
		TestUtil.folder = folder;

		TestUtil.copyFile(IntegrationTest3.class, MAPPING3_PATH);

		TestUtil.copyFile(IntegrationTest3.class, ANNOTATION_INPUT_PATH);
	}

	private TinyRemapper setupRemapper() {
		// copy from Main.java
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

		Path mappings = TestUtil.getFile(IntegrationTest3.MAPPING3_PATH).toPath();

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
	 * This is a test for annotation remapping.
	 */
	@Test
	public void annotation() throws IOException {
		final TinyRemapper remapper = setupRemapper();
		final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;
		final Path[] classpath = new Path[]{};

		Path output = TestUtil.output(ANNOTATION_INPUT_PATH);
		Path input = TestUtil.input(ANNOTATION_INPUT_PATH);

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

		JarFile result = new JarFile(output.toFile());

		result.close();
	}

	@AfterAll
	public static void cleanup() throws IOException {
		TestUtil.folder = null;
	}
}
