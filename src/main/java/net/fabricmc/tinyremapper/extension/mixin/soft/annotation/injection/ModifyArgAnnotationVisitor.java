package net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;

public class ModifyArgAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public ModifyArgAnnotationVisitor(CommonData data, AnnotationVisitor delegate, TrMember method, boolean remap, List<TrClass> targets) {
		super(Annotation.MODIFY_ARG, data, delegate, method, remap, targets);
	}
}
