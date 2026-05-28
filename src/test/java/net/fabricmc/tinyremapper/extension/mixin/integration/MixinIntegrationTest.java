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

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import net.fabricmc.tinyremapper.extension.mixin.integration.mixins.AmbiguousRemappedNameMixin;
import net.fabricmc.tinyremapper.extension.mixin.integration.mixins.DescAtMixin;
import net.fabricmc.tinyremapper.extension.mixin.integration.mixins.LvtRemapTargetMixin;
import net.fabricmc.tinyremapper.extension.mixin.integration.mixins.NonObfuscatedOverrideMixin;
import net.fabricmc.tinyremapper.extension.mixin.integration.mixins.RegexMethodTargetMixin;
import net.fabricmc.tinyremapper.extension.mixin.integration.mixins.SeparateRemappedNameMixin;
import net.fabricmc.tinyremapper.extension.mixin.integration.mixins.WildcardTargetMixin;
import net.fabricmc.tinyremapper.extension.mixin.integration.targets.AmbiguousRemappedNameTarget;
import net.fabricmc.tinyremapper.extension.mixin.integration.targets.DescAtTarget;
import net.fabricmc.tinyremapper.extension.mixin.integration.targets.LvtRemapTarget;
import net.fabricmc.tinyremapper.extension.mixin.integration.targets.NonObfuscatedOverrideTarget;
import net.fabricmc.tinyremapper.extension.mixin.integration.targets.RegexMethodTarget;
import net.fabricmc.tinyremapper.extension.mixin.integration.targets.SeparateRemappedNameTarget;
import net.fabricmc.tinyremapper.extension.mixin.integration.targets.WildcardTarget;

public class MixinIntegrationTest {
	@TempDir
	Path folder;

	@Test
	public void remapWildcardName() throws IOException {
		String remapped = remap(WildcardTarget.class, WildcardTargetMixin.class, out -> {
			String fqn = "net/fabricmc/tinyremapper/extension/mixin/integration/targets/WildcardTarget";
			out.acceptClass("java/lang/String", "com/example/NotString");
			out.acceptMethod(new IMappingProvider.Member(fqn, "targetA", "(Ljava/lang/Object;)V"), "sameName");
			out.acceptMethod(new IMappingProvider.Member(fqn, "targetA", "()Ljava/lang/String;"), "sameName");
			out.acceptMethod(new IMappingProvider.Member(fqn, "targetB", "()Ljava/lang/Object;"), "sameName");
		});

		// Check constructor inject did not gain a desc
		// <init>* -> <init>*
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"<init>*\"}"));
		// Check that wildcard desc is remapped without a name
		// *()Ljava/lang/String; -> *()Lcom/example/NotString;
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"*()Lcom/example/NotString;\"}"));
		// Check that wildcards are expanded with descriptor to avoid incorrect targets (targetB)
		// targetA* -> {"sameName()Lcom/example/NotString;", "sameName(Ljava/lang/Object;)V"}
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"sameName()Lcom/example/NotString;\", \"sameName(Ljava/lang/Object;)V\"}"));
	}

	@Test
	public void remapInvokeNonObfuscatedOverride() throws IOException {
		String remapped = remap(NonObfuscatedOverrideTarget.class, NonObfuscatedOverrideMixin.class, out -> {
			String fqn = "net/fabricmc/tinyremapper/extension/mixin/integration/targets/NonObfuscatedOverrideTarget";
			out.acceptClass(fqn, "com/example/Obfuscated");
			out.acceptMethod(new IMappingProvider.Member(fqn, "callAdd", "(Ljava/lang/Object;)V"), "obfuscatedCallAdd");
		});

		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"obfuscatedCallAdd\""));
		// Method is implemented in the target class
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/At;(value=\"INVOKE\", target=\"Lcom/example/Obfuscated;add(Ljava/lang/Object;)Z\""));
		// Method is NOT implemented in the target class and instead comes from unobfuscated super class
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/At;(value=\"INVOKE\", target=\"Lcom/example/Obfuscated;addAll(Ljava/util/Collection;)Z\""));
	}

	@Test
	public void remapAmbiguousRemappedName() throws IOException {
		String remapped = remap(AmbiguousRemappedNameTarget.class, AmbiguousRemappedNameMixin.class, out -> {
			String fqn = "net/fabricmc/tinyremapper/extension/mixin/integration/targets/AmbiguousRemappedNameTarget";
			out.acceptClass(fqn, "com/example/Remapped");
			out.acceptMethod(new IMappingProvider.Member(fqn, "addString", "(Ljava/lang/String;)V"), "add");
			out.acceptMethod(new IMappingProvider.Member(fqn, "addList", "(Ljava/util/List;)V"), "add");
		});

		// full signature is used to disambiguate names
		// addString -> add(Ljava/lang/String;)V
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"add(Ljava/lang/String;)V\""));
	}

	@Test
	public void remapSeparateRemappedName() throws IOException {
		String remapped = remap(SeparateRemappedNameTarget.class, SeparateRemappedNameMixin.class, out -> {
			String fqn = "net/fabricmc/tinyremapper/extension/mixin/integration/targets/SeparateRemappedNameTarget";
			out.acceptMethod(new IMappingProvider.Member(fqn, "addString", "(Ljava/lang/String;)V"), "add1");
			out.acceptMethod(new IMappingProvider.Member(fqn, "addString", "(Ljava/lang/String;I)V"), "add2");
		});

		// Ensure that descriptor isn't added and first method is targeted
		// addString -> add1
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"add1\"}"));
		// Ensure that descriptor is kept and second method is targeted
		// addString(Ljava/lang/String;I)V -> add2(Ljava/lang/String;I)V
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"add2(Ljava/lang/String;I)V\"}"));
		// Ensure that both methods are targeted by wildcard
		// addString* -> {"add1*", "add2*"}
		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Inject;(method={\"add1*\", \"add2*\"}"));
	}

	@Test
	public void remapLvtName() throws IOException {
		String remapped = remap(LvtRemapTarget.class, LvtRemapTargetMixin.class, out -> {
			String fqn = "net/fabricmc/tinyremapper/extension/mixin/integration/targets/LvtRemapTarget";
			out.acceptClass(fqn, "com/example/Remapped");
			IMappingProvider.Member member = new IMappingProvider.Member(fqn, "target", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
			out.acceptMethod(member, "targetRemapped");
			out.acceptMethodArg(member, 1, "remappedStr1");
			out.acceptMethodArg(member, 2, "remappedStr2");
			out.acceptMethodArg(member, 3, "remappedStr3");
			out.acceptMethodArg(member, 4, "remappedStr4");
		});

		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/ModifyVariable;(method={\"targetRemapped\"}, at=@Lorg/spongepowered/asm/mixin/injection/At;(value=\"HEAD\"), name={\"remappedStr3\"})"));
	}

	@Test
	public void remapDescAt() throws IOException {
		String remapped = remap(DescAtTarget.class, DescAtMixin.class, out -> {
			String fqn = "net/fabricmc/tinyremapper/extension/mixin/integration/targets/DescAtTarget";
			out.acceptClass(fqn, "com/example/Remapped");
			out.acceptMethod(new IMappingProvider.Member(fqn, "mainTarget", "(I)V"), "mainTargetRemapped");
			out.acceptMethod(new IMappingProvider.Member(fqn, "atTarget", "(Ljava/lang/String;)I"), "at");
		});

		assertTrue(remapped.contains("@Lorg/spongepowered/asm/mixin/injection/Desc;(args={java.lang.String.class}, ret=int.class, value=\"at\""));
	}

	@Test
	public void remapRegexMethodTarget() throws IOException {
		String remapped = remap(RegexMethodTarget.class, RegexMethodTargetMixin.class, out -> {
			String fqn = "net/fabricmc/tinyremapper/extension/mixin/integration/targets/RegexMethodTarget";
			out.acceptClass(fqn, "com/example/Remapped");
			out.acceptMethod(new IMappingProvider.Member(fqn, "target0", "()Ljava/lang/String;"), "t0");
			out.acceptMethod(new IMappingProvider.Member(fqn, "target0", "(Ljava/lang/String;)Ljava/lang/String;"), "t00");
			out.acceptMethod(new IMappingProvider.Member(fqn, "target1", "()Ljava/lang/String;"), "t1");
			out.acceptMethod(new IMappingProvider.Member(fqn, "target2", "(Ljava/util/List;)Ljava/lang/String;"), "t2");
			out.acceptMethod(new IMappingProvider.Member(fqn, "target3", "(Ljava/lang/String;)V"), "t3");
			out.acceptMethod(new IMappingProvider.Member(fqn, "thing4", "()Ljava/lang/String;"), "thing");
		});

		assertTrue(remapped.contains("method={\"Lcom/example/Remapped;t0()Ljava/lang/String;\", \"Lcom/example/Remapped;t00(Ljava/lang/String;)Ljava/lang/String;\", \"Lcom/example/Remapped;t1()Ljava/lang/String;\", \"Lcom/example/Remapped;t2(Ljava/util/List;)Ljava/lang/String;\"}"));
	}

	private String remap(Class<?> target, Class<?> mixin, IMappingProvider mappings) throws IOException {
		Path classpath = createJar(target);
		Path input = createJar(mixin);
		Path output = folder.resolve("output.jar");

		TinyRemapper tinyRemapper = TinyRemapper.newRemapper()
				.extension(new MixinExtension())
				.withMappings(mappings)
				.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			tinyRemapper.readClassPath(classpath);
			tinyRemapper.readInputs(input);

			tinyRemapper.apply(outputConsumer);
		}

		return textify(output, mixin);
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
