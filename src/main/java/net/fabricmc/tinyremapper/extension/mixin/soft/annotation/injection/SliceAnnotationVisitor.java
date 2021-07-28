package net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;

public class SliceAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final boolean remap;

	public SliceAnnotationVisitor(CommonData data, AnnotationVisitor delegate, boolean remap) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.remap = remap;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String name, String descriptor) {
		AnnotationVisitor av = super.visitAnnotation(name, descriptor);

		if (name.equals(AnnotationElement.FROM) || name.equals(AnnotationElement.TO)) {
			if (!descriptor.equals(Annotation.AT)) {
				throw new RuntimeException("Unexpected annotation " + descriptor);
			}

			av = new AtAnnotationVisitor(data, av, remap);
		}

		return av;
	}
}
