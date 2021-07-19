package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;

public class ModifyVariableAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public ModifyVariableAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Annotation.MODIFY_VARIABLE.get(), data, remap, targets);
	}
}
