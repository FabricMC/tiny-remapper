package net.fabricmc.tinyremapper.extension.mixin.hard;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.ShadowAnnotationVisitor;

class HardTargetMixinFieldVisitor extends FieldVisitor {
	private final CommonData data;
	private final MxMember field;

	private final boolean remap;
	private final List<String> targets;

	HardTargetMixinFieldVisitor(CommonData data, FieldVisitor delegate, MxMember field,
								boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.field = Objects.requireNonNull(field);

		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

		if (Annotation.SHADOW.equals(descriptor)) {
			av = new ShadowAnnotationVisitor(data, av, field, remap, targets);
		}

		return av;
	}
}
