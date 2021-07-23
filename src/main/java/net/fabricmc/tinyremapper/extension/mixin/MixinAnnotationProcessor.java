package net.fabricmc.tinyremapper.extension.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.objectweb.asm.ClassVisitor;

import net.fabricmc.tinyremapper.api.TrEnvironment;
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
	private final List<Consumer<CommonData>> tasks;

	public MixinAnnotationProcessor() {
		this.logger = new Logger();
		this.tasks = new ArrayList<>();
	}

	public MixinAnnotationProcessor(Logger.Level logLevel) {
		this.logger = new Logger(logLevel);
		this.tasks = new ArrayList<>();
	}

	public ClassVisitor getAnalyzeVisitor(ClassVisitor cv) {
		return new HardTargetMixinClassVisitor(tasks, cv);
	}

	public void process(TrEnvironment environment) {
		CommonData data = new CommonData(environment, logger);
		tasks.forEach(task -> task.accept(data));
	}

	public ClassVisitor getPreVisitor(ClassVisitor cv, TrEnvironment environment) {
		return new SoftTargetMixinClassVisitor(new CommonData(environment, logger), cv);
	}
}

