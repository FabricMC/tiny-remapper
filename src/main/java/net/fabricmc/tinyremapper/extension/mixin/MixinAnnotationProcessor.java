package net.fabricmc.tinyremapper.extension.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.objectweb.asm.ClassVisitor;

import net.fabricmc.tinyremapper.TinyRemapper.AnalyzeVisitorProvider;
import net.fabricmc.tinyremapper.TinyRemapper.ApplyVisitorProvider;
import net.fabricmc.tinyremapper.TinyRemapper.StateProcessor;
import net.fabricmc.tinyremapper.api.TrClass;
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
public class MixinAnnotationProcessor implements AnalyzeVisitorProvider, ApplyVisitorProvider, StateProcessor {
	private final Logger logger;
	private final Map<Integer, List<Consumer<CommonData>>> tasks;

	public MixinAnnotationProcessor() {
		this.logger = new Logger();
		this.tasks = new HashMap<>();
	}

	public MixinAnnotationProcessor(Logger.Level logLevel) {
		this.logger = new Logger(logLevel);
		this.tasks = new HashMap<>();
	}

	@Override
	public ClassVisitor insertAnalyzeVisitor(int mrjVersion, String className, ClassVisitor next) {
		tasks.putIfAbsent(mrjVersion, new ArrayList<>());
		return new HardTargetMixinClassVisitor(tasks.get(mrjVersion), next);
	}

	@Override
	public void process(TrEnvironment environment) {
		CommonData data = new CommonData(environment, logger);
		tasks.get(environment.getMrjVersion()).forEach(task -> {
			try {
				task.accept(data);
			} catch (RuntimeException e) {
				logger.error(e.getMessage());
			}
		});
	}

	@Override
	public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
		return new SoftTargetMixinClassVisitor(new CommonData(cls.getEnvironment(), logger), next);
	}
}

