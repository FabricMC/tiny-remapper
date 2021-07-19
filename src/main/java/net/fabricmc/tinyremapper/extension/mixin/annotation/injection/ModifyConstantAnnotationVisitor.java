package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;

public class ModifyConstantAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public ModifyConstantAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Annotation.MODIFY_CONSTANT.get(), data, remap, targets);
	}
}
