package net.fabricmc.tinyremapper.extension.mixin.soft;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.AccessorAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.InvokerAnnotationVisitor;

class SoftTargetMixinMethodVisitor extends MethodVisitor {
	private final CommonData data;
	private final TrMember method;

	private final boolean remap;
	private final List<TrClass> targets;

	SoftTargetMixinMethodVisitor(CommonData data, MethodVisitor delegate, TrMember method, boolean remap, List<TrClass> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.method = Objects.requireNonNull(method);

		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

		if (Annotation.ACCESSOR.equals(descriptor)) {
			av = new AccessorAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.INVOKER.equals(descriptor)) {
			av = new InvokerAnnotationVisitor(data, av, method, remap, targets);
		}

		return av;
	}
}
