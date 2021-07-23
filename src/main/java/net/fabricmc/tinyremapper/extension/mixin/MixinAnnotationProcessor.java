package net.fabricmc.tinyremapper.extension.mixin;

import org.objectweb.asm.ClassVisitor;

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrRemapper;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.hard.HardTargetMixinClassVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.SoftTargetMixinClassVisitor;

/**
 * Remap mixin annotation.
 * <p>Soft-target: Mixin, Invoker, Accessor, Inject, ModifyArg, ModifyArgs, Redirect, ModifyVariable, ModifyConstant, At, Slice.</p>
 * <p>Hard-target: Shadow, Overwrite, Accessor, Invoker, Implements.</p>
 */
public class MixinAnnotationProcessor {
	private final Logger logger;

	public MixinAnnotationProcessor() {
		this.logger = new Logger();
	}

	public MixinAnnotationProcessor(Logger.Level logLevel) {
		this.logger = new Logger(logLevel);
	}

	public ClassVisitor getPostPropagationVisitor(ClassVisitor cv, TrRemapper remapper, TrEnvironment environment) {
		return new HardTargetMixinClassVisitor(new CommonData(remapper, environment, logger), cv);
	}

	public ClassVisitor getPreVisitor(ClassVisitor cv, TrRemapper remapper, TrEnvironment environment) {
		return new SoftTargetMixinClassVisitor(new CommonData(remapper, environment, logger), cv);
	}
}

