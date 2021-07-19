package net.fabricmc.tinyremapper.extension.mixin.asm;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.factory.MethodAnnotationVisitorFactory;

/**
 * Required order: {@code @Shadow}, {@code @Overwrite}, etc
 * <p>Pass 1: visit annotations.</p>
 */
class MixinExtensionMethodVisitor extends MethodVisitor {
	private final CommonDataHolder data;
	private final boolean remap;
	private final List<String> targets;

	MixinExtensionMethodVisitor(MethodVisitor delegate, CommonDataHolder data,
								boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor annotationVisitor = super.visitAnnotation(descriptor, visible);
		MethodAnnotationVisitorFactory factory = new MethodAnnotationVisitorFactory(
				data, annotationVisitor);

		if (Annotation.SHADOW.get().equals(descriptor)) {
			annotationVisitor = factory.shadow(remap, targets);
		} else if (Annotation.OVERWRITE.get().equals(descriptor)) {
			annotationVisitor = factory.overwrite(remap, targets);
		} else if (Annotation.ACCESSOR.get().equals(descriptor)) {
			annotationVisitor = factory.accessor(remap, targets);
		} else if (Annotation.INVOKER.get().equals(descriptor)) {
			annotationVisitor = factory.invoker(remap, targets);
		} else if (Annotation.DESCRIPTORS.get().equals(descriptor)) {
			annotationVisitor = factory.descriptors(remap, targets);
		} else if (Annotation.DESC.get().equals(descriptor)) {
			annotationVisitor = factory.desc(remap, targets);
		} else if (Annotation.INJECT.get().equals(descriptor)) {
			annotationVisitor = factory.inject(remap, targets);
		} else if (Annotation.MODIFY_ARG.get().equals(descriptor)) {
			annotationVisitor = factory.modifyArg(remap, targets);
		} else if (Annotation.MODIFY_ARGS.get().equals(descriptor)) {
			annotationVisitor = factory.modifyArgs(remap, targets);
		} else if (Annotation.REDIRECT.get().equals(descriptor)) {
			annotationVisitor = factory.redirect(remap, targets);
		} else if (Annotation.MODIFY_CONSTANT.get().equals(descriptor)) {
			annotationVisitor = factory.modifyConstant(remap, targets);
		} else if (Annotation.MODIFY_VARIABLE.get().equals(descriptor)) {
			annotationVisitor = factory.modifyVariable(remap, targets);
		}

		return annotationVisitor;
	}
}
