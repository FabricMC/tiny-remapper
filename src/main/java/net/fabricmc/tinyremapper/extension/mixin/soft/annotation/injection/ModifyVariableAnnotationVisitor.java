package net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;

public class ModifyVariableAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public ModifyVariableAnnotationVisitor(CommonData data, AnnotationVisitor delegate, boolean remap, List<String> targets) {
		super(Annotation.MODIFY_VARIABLE, data, delegate, remap, targets);
	}
}
