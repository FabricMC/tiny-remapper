package net.fabricmc.tinyremapper.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TestUtil;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

class TrClassTest {
	private static final String MAPPING1_PATH = "/mapping/mapping1.tiny";
	private static final String INTERFACE_INPUT_PATH = "/integration/api/interface-input.jar";

	@TempDir
	public static Path folder;

	@BeforeAll
	public static void setup() throws IOException {
		TestUtil.folder = folder;

		TestUtil.copyFile(net.fabricmc.tinyremapper.IntegrationTest1.class, MAPPING1_PATH);
		TestUtil.copyFile(net.fabricmc.tinyremapper.IntegrationTest1.class, INTERFACE_INPUT_PATH);
	}

	@Test
	void resolveInterfaces() {
		final String from = "a";
		final String to = "b";

		Path mappings = TestUtil.getFile(TrClassTest.MAPPING1_PATH).toPath();

		Path output = TestUtil.output(INTERFACE_INPUT_PATH);
		Path input = TestUtil.input(INTERFACE_INPUT_PATH);

		TinyRemapper tr = TinyRemapper.newRemapper()
				.withMappings(TinyUtils.createTinyMappingProvider(mappings, from, to))
				.extraPreVisitor((c, r, e) -> {
					return new ClassVisitor(Opcodes.ASM9, c) {
						@Override
						public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
							List<String> ifaces = e.getClass(name).resolveInterfaces();

							if (name.equals("com/github/logicfan/Main")) {
								Assertions.assertEquals(ifaces.get(2), "com/github/logicfan/I3");
								Assertions.assertEquals(ifaces.get(3), "com/github/logicfan/I4");
							} else if (name.equals("com/github/logicfan/I1")) {
								Assertions.assertEquals(ifaces.get(1), "com/github/logicfan/I4");
							}

							System.out.println(name + " : " + String.join(" , ", ifaces));
							super.visit(version, access, name, signature, superName, interfaces);
						}
					};
				})
				.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			tr.readInputs(input);

			tr.apply(outputConsumer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			tr.finish();
		}
	}
}
