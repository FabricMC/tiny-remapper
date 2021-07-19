package net.fabricmc.tinyremapper.extension.mixin.factory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.ImplementsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.ImplementsAnnotationVisitor.Interface;
import net.fabricmc.tinyremapper.extension.mixin.annotation.MixinAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.DescAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.injection.DescriptorsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;

public final class ClassAnnotationVisitorFactory {
	private final CommonDataHolder data;

	public ClassAnnotationVisitorFactory(CommonDataHolder data, AnnotationVisitor delegate) {
		this.data = data.addAnnotation(delegate, AnnotationType.CLASS);
	}

	public AnnotationVisitor mixin(AtomicBoolean remapOut, List<String> targetsOut) {
		return new MixinAnnotationVisitor(data, remapOut, targetsOut);
	}

	public AnnotationVisitor _implements(List<Interface> interfacesOut) {
		return new ImplementsAnnotationVisitor(data, interfacesOut);
	}

	public AnnotationVisitor desc(boolean remap, List<String> targets) {
		return new DescAnnotationVisitor(data, remap, targets);
	}

	public AnnotationVisitor descriptors(boolean remap, List<String> targets) {
		return new DescriptorsAnnotationVisitor(data, remap, targets);
	}
}
