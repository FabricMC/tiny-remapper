package net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;

public class InjectAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public InjectAnnotationVisitor(CommonData data, AnnotationVisitor delegate, boolean remap, List<String> targets) {
		super(Annotation.INJECT, data, delegate, remap, targets);
	}
}
