package net.fabricmc.tinyremapper.extension.mixin.factory;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.AccessorAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.InvokerAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.OverwriteAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.ShadowAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.DescAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.DescriptorsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.InjectAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.ModifyArgAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.ModifyArgsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.ModifyConstantAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.ModifyVariableAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.RedirectAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;

public final class MethodAnnotationVisitorFactory {
	private final CommonDataHolder data;

	public MethodAnnotationVisitorFactory(CommonDataHolder data, AnnotationVisitor delegate) {
		this.data = data.addAnnotation(delegate, AnnotationType.METHOD);
	}

	public AnnotationVisitor shadow(boolean remap, List<String> targets) {
		return new ShadowAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor overwrite(boolean remap, List<String> targets) {
		return new OverwriteAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor accessor(boolean remap, List<String> targets) {
		return new AccessorAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor invoker(boolean remap, List<String> targets) {
		return new InvokerAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor inject(boolean remap, List<String> targets) {
		return new InjectAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor desc(boolean remap, List<String> targets) {
		return new DescAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor descriptors(boolean remap, List<String> targets) {
		return new DescriptorsAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor modifyArg(boolean remap, List<String> targets) {
		return new ModifyArgAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor modifyArgs(boolean remap, List<String> targets) {
		return new ModifyArgsAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor redirect(boolean remap, List<String> targets) {
		return new RedirectAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor modifyConstant(boolean remap, List<String> targets) {
		return new ModifyConstantAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor modifyVariable(boolean remap, List<String> targets) {
		return new ModifyVariableAnnotationVisitor(data, remap, targets);
	}
}
