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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

public class IntegrationTest1 {
    private static final TemporaryFolder FOLDER = new TemporaryFolder();
    private static final String MAPPING_PATH = "/mapping/mapping1.tiny";
    private static final String BASIC_INPUT_PATH = "/integration/basic/input.jar";
    private static final String MRJ1_INPUT_PATH = "/integration/mrj1/input.jar";

    private static File mappingFile;
    private static File basicInputFile;
    private static File mrj1InputFile;

    @BeforeClass
    public static void setup() throws IOException {
        // setup test environment
        FOLDER.create();

        mappingFile = TestUtil.copyFile(IntegrationTest1.class, FOLDER, MAPPING_PATH);
        basicInputFile = TestUtil.copyFile(IntegrationTest1.class, FOLDER, BASIC_INPUT_PATH);
        mrj1InputFile = TestUtil.copyFile(IntegrationTest1.class, FOLDER, MRJ1_INPUT_PATH);
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

        Path mappings = mappingFile.toPath();

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

    @Test
    public void basic() throws IOException {
        final TinyRemapper remapper = setupRemapper();
        final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;

        final Path output = Paths.get(FOLDER.getRoot().getPath(), "basic_output.jar");
        final Path input = basicInputFile.toPath();
        final Path[] classpath = new Path[]{};

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
    }

    @Test
    public void mrj1() throws IOException {
        final TinyRemapper remapper = setupRemapper();
        final NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;

        final Path output = Paths.get(FOLDER.getRoot().getPath(), "mrj1_output.jar");
        final Path input = mrj1InputFile.toPath();
        final Path[] classpath = new Path[]{};

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
    }

    @AfterClass
    public static void cleanup() {
        FOLDER.delete();
    }
}