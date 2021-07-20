package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;
import net.fabricmc.tinyremapper.extension.mixin.annotation.common.FirstPassAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.common.LoggerOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;

/**
 * Required order: [{@code args} | {@code owner} | {@code ret}] {@code value}.
 * <p>Pass 1: read args, owner and ret.</p>
 * <p>Pass 2: read and remap value.</p>
 */
public class DescAnnotationVisitor extends DescFirstPassAnnotationVisitor {
	public DescAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(data, remap, targets);
	}
}

class DescFirstPassAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonDataHolderOld data;

	private List<String> targets;
	private final List<String> args;
	private String ret;

	DescFirstPassAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(Annotation.DESC, remap);

		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
		this.args = new ArrayList<>();
		this.ret = "V";
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.RET.get())) {
			Type type = Objects.requireNonNull((Type) value);
			this.ret = type.getDescriptor();
		} else if (name.equals(AnnotationElement.OWNER.get())) {
			Type type = Objects.requireNonNull((Type) value);
			this.targets = Collections.singletonList(type.getInternalName());
		}

		super.visit(name, value);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor annotationVisitor = super.visitArray(name);

		if (name.equals(AnnotationElement.ARGS.get())) {
			return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
				@Override
				public void visit(String name, Object value) {
					Type type = Objects.requireNonNull((Type) value);
					DescFirstPassAnnotationVisitor.this.args.add(type.getDescriptor());
					super.visit(name, value);
				}
			};
		} else {
			return annotationVisitor;
		}
	}

	@Override
	public void visitEnd() {
		if (super.remap) {
			this.accept(new DescSecondPassAnnotationVisitor(data, targets, args, ret));
		} else {
			this.accept(data.delegate);
		}

		super.visitEnd();
	}
}

class DescSecondPassAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolderOld data;
	private final List<String> owners;
	private final List<String> args;
	private final String ret;

	DescSecondPassAnnotationVisitor(CommonDataHolderOld data, List<String> owners, List<String> args, String ret) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.owners = Objects.requireNonNull(owners);
		this.args = Objects.requireNonNull(args);
		this.ret = Objects.requireNonNull(ret);
	}

	private String getFieldDesc() {
		return this.ret;
	}

	private String getMethodDesc() {
		return "(" + String.join("", this.args) + ")" + this.ret;
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.VALUE.get())) {
			String srcName = Objects.requireNonNull((String) value);
			String dstName = srcName;

			String fieldDstName = CommonUtility.remap(
					data.remapper, AnnotationType.FIELD,
					this.owners, srcName, getFieldDesc());

			String methodDstName = CommonUtility.remap(
					data.remapper, AnnotationType.METHOD,
					this.owners, srcName, getMethodDesc());

			if (srcName.equals(fieldDstName)) {
				dstName = methodDstName;
			} else if (srcName.equals(methodDstName)) {
				dstName = fieldDstName;
			} else if (fieldDstName.equals(methodDstName)) {
				dstName = fieldDstName;
			} else {
				LoggerOld.error("Detect conflict mapping " + srcName + " -> " + fieldDstName + "; "
						+ srcName + " -> " + methodDstName + ". This is a serious issue!");
			}

			if (srcName.equals(dstName)) {
				LoggerOld.remapFail("@Desc", owners, data.className, srcName);
			}

			value = dstName;
		}

		super.visit(name, value);
	}
}
