package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;

public class ModifyArgAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public ModifyArgAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(Annotation.MODIFY_ARG, data, remap, targets);
	}
}
