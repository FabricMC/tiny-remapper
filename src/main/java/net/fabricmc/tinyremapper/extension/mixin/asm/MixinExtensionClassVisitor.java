package net.fabricmc.tinyremapper.extension.mixin.asm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.extension.mixin.annotation.ImplementsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.annotation.ImplementsAnnotationVisitor.Interface;
import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.data.IMappingHolder;
import net.fabricmc.tinyremapper.extension.mixin.factory.ClassAnnotationVisitorFactory;

/**
 * Required order: [@Mixin | @Implements] [method | field | @Descriptors].
 * <p>Pass 1: @Mixin, @Implements, methods and fields.</p>
 * <p>Pass 2: @Descriptors.</p>
 */
public class MixinExtensionClassVisitor extends FirstPassClassVisitor {
	public MixinExtensionClassVisitor(ClassVisitor delegate, Remapper remapper, IMappingHolder mapping, TrEnvironment environment) {
		super(delegate, remapper, mapping, environment);
	}
}

class FirstPassClassVisitor extends ClassNode {
	CommonDataHolderOld data;
	ClassVisitor delegate;

	// @Mixin
	private final AtomicBoolean remap = new AtomicBoolean();
	private final List<String> targets = new ArrayList<>();

	// @Implements
	private final List<Interface> interfaces = new ArrayList<>();

	FirstPassClassVisitor(ClassVisitor delegate, Remapper remapper,
									IMappingHolder mapping, TrEnvironment environment) {
		super(Constant.ASM_VERSION);
		this.delegate = Objects.requireNonNull(delegate);
		this.data = new CommonDataHolderOld(remapper, environment, mapping);
	}

	/**
	 * This is called before visitAnnotation.
	 */
	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.data = this.data.addClassName(name);
		super.visit(version, access, name, signature, superName, interfaces);
	}

	/**
	 * This is called before visitMethod & visitField.
	 */
	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor annotationVisitor = super.visitAnnotation(descriptor, visible);
		ClassAnnotationVisitorFactory factory = new ClassAnnotationVisitorFactory(
				data, annotationVisitor);

		if (Annotation.MIXIN.equals(descriptor)) {
			annotationVisitor = factory.mixin(remap, targets);
		} else if (Annotation.IMPLEMENTS.equals(descriptor)) {
			annotationVisitor = factory._implements(interfaces);
		}

		return annotationVisitor;
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		FieldVisitor fieldVisitor = super.visitField(access, name, descriptor, signature, value);
		CommonDataHolderOld fieldData = data.addMember(name, descriptor);

		if (targets.isEmpty()) {
			return fieldVisitor;
		} else {
			return new MixinExtensionFieldVisitor(fieldVisitor, fieldData, remap.get(), targets);
		}
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
		CommonDataHolderOld methodData = data.addMember(name, descriptor);
		ImplementsAnnotationVisitor.visitMethod(methodData, interfaces);

		if (targets.isEmpty()) {
			return methodVisitor;
		} else {
			return new MixinExtensionMethodVisitor(methodVisitor, methodData, remap.get(), targets);
		}
	}

	@Override
	public void visitEnd() {
		this.accept(new SecondPassClassVisitor(this.delegate, this.data, remap.get(), targets));

		super.visitEnd();
	}
}

class SecondPassClassVisitor extends ClassVisitor {
	CommonDataHolderOld data;
	private final boolean remap;
	private final List<String> targets;

	SecondPassClassVisitor(ClassVisitor delegate, CommonDataHolderOld data,
								boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor annotationVisitor = super.visitAnnotation(descriptor, visible);
		ClassAnnotationVisitorFactory factory = new ClassAnnotationVisitorFactory(
				data, annotationVisitor);

		if (Annotation.DESCRIPTORS.equals(descriptor)) {
			annotationVisitor = factory.descriptors(remap, targets);
		} else if (Annotation.DESC.equals(descriptor)) {
			annotationVisitor = factory.desc(remap, targets);
		}

		return annotationVisitor;
	}
}
