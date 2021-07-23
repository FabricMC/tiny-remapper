package net.fabricmc.tinyremapper.extension.mixin.common.data;

import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.Opcodes;

public final class Constant {
	public static final int ASM_VERSION = Opcodes.ASM9;

	public static final List<String> UNMAP_NAMES = Arrays.asList(
			"<init>", "<clinit>", "");
}
