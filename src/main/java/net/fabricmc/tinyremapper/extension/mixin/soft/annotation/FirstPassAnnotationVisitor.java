package net.fabricmc.tinyremapper.extension.mixin.soft.annotation;

import java.util.Objects;

import org.objectweb.asm.tree.AnnotationNode;

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;

/**
 * The common annotation visitor for first pass.
 */
public class FirstPassAnnotationVisitor extends AnnotationNode {
	protected boolean remap;

	public FirstPassAnnotationVisitor(String descriptor, boolean remapDefault) {
		super(Constant.ASM_VERSION, descriptor);
		remap = remapDefault;
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP)) {
			remap = Objects.requireNonNull((Boolean) value);
		}

		super.visit(name, value);
	}
}
