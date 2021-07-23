package net.fabricmc.tinyremapper.extension.mixin.hard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxClass;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.ImplementsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.hard.data.SoftInterface;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.MixinAnnotationVisitor;

public class HardTargetMixinClassVisitor extends ClassVisitor {
	private final CommonData data;
	private MxClass _class;

	// @Mixin
	private final AtomicBoolean remap = new AtomicBoolean();
	private final List<String> targets = new ArrayList<>();

	// @Implements
	private final List<SoftInterface> interfaces = new ArrayList<>();

	public HardTargetMixinClassVisitor(CommonData data, ClassVisitor delegate) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
	}

	/**
	 * This is called before visitAnnotation.
	 */
	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this._class = new MxClass(name);
		super.visit(version, access, name, signature, superName, interfaces);
	}

	/**
	 * This is called before visitMethod & visitField.
	 */
	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

		if (Annotation.MIXIN.equals(descriptor)) {
			av = new MixinAnnotationVisitor(data, av, remap, targets);
		} else if (Annotation.IMPLEMENTS.equals(descriptor)) {
			av = new ImplementsAnnotationVisitor(av, interfaces);
		}

		return av;
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
		MxMember field = _class.getField(name, descriptor);

		if (targets.isEmpty()) {
			return fv;
		} else {
			return new HardTargetMixinFieldVisitor(data, fv, field, remap.get(), Collections.unmodifiableList(targets));
		}
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
		MxMember method = _class.getMethod(name, descriptor);

		ImplementsAnnotationVisitor.visitMethod(data, method, interfaces);

		if (targets.isEmpty()) {
			return mv;
		} else {
			return new HardTargetMixinMethodVisitor(data, mv, method, remap.get(), Collections.unmodifiableList(targets));
		}
	}
}
