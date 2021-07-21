package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;

public class SliceAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolderOld data;
	private final boolean remap;
	private final List<String> targets;

	public SliceAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String name, String descriptor) {
		AnnotationVisitor annotationVisitor = super.visitAnnotation(name, descriptor);

		if (name.equals(AnnotationElement.FROM.get()) || name.equals(AnnotationElement.TO.get())) {
			if (!descriptor.equals(Annotation.AT)) {
				throw new RuntimeException("Unexpected annotation " + descriptor);
			}

			CommonDataHolderOld data = this.data.alterAnnotationVisitor(annotationVisitor);
			annotationVisitor = new AtAnnotationVisitor(data, remap, targets);
		}

		return annotationVisitor;
	}
}
