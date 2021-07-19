package net.fabricmc.tinyremapper.extension.mixin.factory;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.ShadowAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;

public final class FieldAnnotationVisitorFactory {
	private final CommonDataHolder data;

	public FieldAnnotationVisitorFactory(CommonDataHolder data, AnnotationVisitor delegate) {
		this.data = data.addAnnotation(delegate, AnnotationType.FIELD);
	}

	public AnnotationVisitor shadow(boolean remap, List<String> targets) {
		return new ShadowAnnotationVisitor(data, remap, targets);
	}
}
