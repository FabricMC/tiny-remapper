package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.List;

import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;

/**
 * Required order: {@code remap} [{@code method} | {@code name} | {@code at} | {@code slice} | {@code target}].
 * <p>Pass 1: read remap.</p>
 * <p>Pass 2: read and remap anything else.</p>
 */
public class InjectAnnotationVisitor extends CommonInjectionAnnotationVisitor {
	public InjectAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(Annotation.INJECT, data, remap, targets);
	}
}
