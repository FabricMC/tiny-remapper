package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;

/**
 * Required order: {@code remap} [{@code method} | {@code name} | {@code at} | {@code slice} | {@code target}].
 * <p>Pass 1: read remap.</p>
 * <p>Pass 2: read and remap anything else.</p>
 */
public class InjectAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public InjectAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Annotation.INJECT.get(), data, remap, targets);
	}
}
