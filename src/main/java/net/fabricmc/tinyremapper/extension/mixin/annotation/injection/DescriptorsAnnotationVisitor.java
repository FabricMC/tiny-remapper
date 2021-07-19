package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;

public class DescriptorsAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolder data;
	private final boolean remap;
	private final List<String> targets;

	public DescriptorsAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor annotationVisitor = super.visitArray(name);

		if (name.equals(AnnotationElement.VALUE.get())) {
			return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					CommonDataHolder data = DescriptorsAnnotationVisitor.this.data.alterAnnotationVisitor(
							super.visitAnnotation(name, descriptor));
					return new DescAnnotationVisitor(data, remap, targets);
				}
			};
		} else {
			return annotationVisitor;
		}
	}
}
