package net.fabricmc.tinyremapper.extension.mixin;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.extension.mixin.asm.MixinExtensionClassVisitor;
import net.fabricmc.tinyremapper.extension.mixin.data.IMappingHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.SimpleMappingHolder;

/**
 * Currently remap mixin annotation.
 * <p>Soft-target: Mixin, Invoker, Accessor, Inject, ModifyArg, ModifyArgs, Redirect, ModifyVariable, ModifyConstant, At, Slice.</p>
 * <p>Hard-target: Shadow, Overwrite, Accessor, Invoker.</p>
 */
public class MixinAnnotationProcessor {
	private final IMappingHolder mapping = new SimpleMappingHolder();

	public ClassVisitor getPreVisitor(ClassVisitor cv, Remapper remapper, TrEnvironment environment) {
		return new MixinExtensionClassVisitor(cv, remapper, mapping, environment);
	}

	public IMappingProvider getMapping() {
		return mapping;
	}
}

