package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;

public class RedirectAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public RedirectAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(Annotation.REDIRECT, data, remap, targets);
	}
}
