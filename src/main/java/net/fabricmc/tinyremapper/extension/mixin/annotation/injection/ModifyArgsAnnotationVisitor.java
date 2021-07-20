package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;

public class ModifyArgsAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public ModifyArgsAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(Annotation.MODIFY_ARGS, data, remap, targets);
	}
}
