package net.fabricmc.tinyremapper.extension.mixin.soft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.MixinAnnotationVisitor;

public class SoftTargetMixinClassVisitor extends ClassVisitor {
	private final CommonData data;
	private TrClass _class;

	// @Mixin
	private final AtomicBoolean remap = new AtomicBoolean();
	private final List<String> targets = new ArrayList<>();

	public SoftTargetMixinClassVisitor(CommonData data, ClassVisitor delegate) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
	}

	/**
	 * This is called before visitAnnotation.
	 */
	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this._class = Objects.requireNonNull(this.data.environment.getClass(name));
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
		}

		return av;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
		TrMember method = _class.getMethod(name, descriptor);

		if (targets.isEmpty()) {
			return mv;
		} else {
			return new SoftTargetMixinMethodVisitor(data, mv, method, remap.get(), Collections.unmodifiableList(targets));
		}
	}
}
