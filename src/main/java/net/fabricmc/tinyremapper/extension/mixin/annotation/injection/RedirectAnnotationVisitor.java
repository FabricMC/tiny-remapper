package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;

public class RedirectAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public RedirectAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Annotation.REDIRECT.get(), data, remap, targets);
	}
}
