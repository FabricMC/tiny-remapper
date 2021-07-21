package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;

public class ModifyConstantAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public ModifyConstantAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(Annotation.MODIFY_CONSTANT, data, remap, targets);
	}
}
