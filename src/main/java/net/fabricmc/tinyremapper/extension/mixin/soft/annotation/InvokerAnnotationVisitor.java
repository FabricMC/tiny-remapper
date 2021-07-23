package net.fabricmc.tinyremapper.extension.mixin.soft.annotation;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.soft.util.NamedMappable;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 */
public class InvokerAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonData data;
	private final AnnotationVisitor delegate;
	private final TrMember method;

	private final List<TrClass> targets;

	public InvokerAnnotationVisitor(CommonData data, AnnotationVisitor delegate, TrMember method, boolean remap, List<TrClass> targets) {
		super(Annotation.INVOKER, remap);

		this.data = Objects.requireNonNull(data);
		this.delegate = Objects.requireNonNull(delegate);
		this.method = Objects.requireNonNull(method);

		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visitEnd() {
		if (super.remap) {
			this.accept(new InvokerSecondPassAnnotationVisitor(data, delegate, method, targets));
		} else {
			this.accept(delegate);
		}

		super.visitEnd();
	}

	private static class InvokerSecondPassAnnotationVisitor extends AnnotationVisitor {
		private final CommonData data;
		private final TrMember method;
		private final List<TrClass> targets;

		InvokerSecondPassAnnotationVisitor(CommonData data, AnnotationVisitor delegate, TrMember method, List<TrClass> targets) {
			super(Constant.ASM_VERSION, delegate);

			this.data = Objects.requireNonNull(data);
			this.method = Objects.requireNonNull(method);
			this.targets = Objects.requireNonNull(targets);
		}

		@Override
		public void visit(String name, Object value) {
			if (name.equals(AnnotationElement.VALUE)) {
				String methodName = Objects.requireNonNull((String) value);
				String methodDesc = method.getDesc();

				value = new NamedMappable(data, methodName, methodDesc, targets).result();
			}

			super.visit(name, value);
		}
	}
}
