package net.fabricmc.tinyremapper.extension.mixin.hard;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.AccessorAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.InvokerAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.OverwriteAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.ShadowAnnotationVisitor;

class HardTargetMixinMethodVisitor extends MethodVisitor {
	private final CommonData data;
	private final TrMember method;

	private final boolean remap;
	private final List<String> targets;

	HardTargetMixinMethodVisitor(CommonData data, MethodVisitor delegate, TrMember method, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.method = Objects.requireNonNull(method);

		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

		if (Annotation.SHADOW.equals(descriptor)) {
			av = new ShadowAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.OVERWRITE.equals(descriptor)) {
			av = new OverwriteAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.ACCESSOR.equals(descriptor)) {
			av = new AccessorAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.INVOKER.equals(descriptor)) {
			av = new InvokerAnnotationVisitor(data, av, method, remap, targets);
		}

		return av;
	}
}
