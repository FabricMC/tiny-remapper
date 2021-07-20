package net.fabricmc.tinyremapper.extension.mixin.asm;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.factory.FieldAnnotationVisitorFactory;

/**
 * Required order: {@code @Shadow}
 * <p>Pass 1: visit @Shadow.</p>
 */
class MixinExtensionFieldVisitor extends FieldVisitor {
	private final CommonDataHolderOld data;
	private final boolean remap;
	private final List<String> targets;

	MixinExtensionFieldVisitor(FieldVisitor delegate, CommonDataHolderOld data,
									boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor annotationVisitor = super.visitAnnotation(descriptor, visible);
		FieldAnnotationVisitorFactory factory = new FieldAnnotationVisitorFactory(data, annotationVisitor);

		if (Annotation.SHADOW.equals(descriptor)) {
			annotationVisitor = factory.shadow(remap, targets);
		}

		return annotationVisitor;
	}
}
