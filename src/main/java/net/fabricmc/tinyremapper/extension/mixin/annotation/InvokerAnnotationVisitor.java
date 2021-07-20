package net.fabricmc.tinyremapper.extension.mixin.annotation;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;
import net.fabricmc.tinyremapper.extension.mixin.annotation.common.FirstPassAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;

/**
 * Required order: {@code remap} [{@code value} | emit]
 * <p>Pass 1: read remap.</p>
 * <p>Pass 2: read value; remap value or emit mapping.</p>
 */
public class InvokerAnnotationVisitor extends InvokerFirstPassAnnotationVisitor {
	public InvokerAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(data, remap, targets);
	}
}

class InvokerFirstPassAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonDataHolder data;
	private final List<String> targets;

	InvokerFirstPassAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Annotation.INVOKER, remap);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visitEnd() {
		if (super.remap) {
			this.accept(new InvokerSecondPassAnnotationVisitor(this.data, this.targets));
		} else {
			this.accept(this.data.delegate);
		}

		super.visitEnd();
	}
}

class InvokerSecondPassAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolder data;
	private final List<String> targets;

	private String value;

	InvokerSecondPassAnnotationVisitor(CommonDataHolder data, List<String> targets) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.VALUE.get())) {
			InvokerSecondPassAnnotationVisitor.this.value = Objects.requireNonNull((String) value);

			String srcName = InvokerSecondPassAnnotationVisitor.this.value;
			String srcDesc = data.memberDesc;

			String dstName = CommonUtility.remap(
					data.remapper, AnnotationType.METHOD,
					InvokerSecondPassAnnotationVisitor.this.targets, srcName, srcDesc);

			if (srcName.equals(dstName) && !Constant.UNMAP_NAMES.contains(srcName)) {
				Logger.remapFail("@Invoker", InvokerSecondPassAnnotationVisitor.this.targets, data.className, srcName);
			}

			value = dstName;
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (InvokerSecondPassAnnotationVisitor.this.value.isEmpty()) {
			// only remap hard-target if no value is specify
			String prefix;

			if (data.memberName.startsWith("call")) {
				prefix = "call";
			} else if (data.memberName.startsWith("invoke")) {
				prefix = "invoke";
			} else {
				throw new RuntimeException(data.memberName + " does not start with call or invoke.");
			}

			String srcName = CommonUtility.removeCamelPrefix(prefix, data.memberName);
			String srcDesc = data.memberDesc;

			String dstName = CommonUtility.remap(
					data.remapper, AnnotationType.METHOD,
					InvokerSecondPassAnnotationVisitor.this.targets, srcName, srcDesc);

			if (srcName.equals(dstName) && !Constant.UNMAP_NAMES.contains(srcName)) {
				Logger.remapFail("@Invoker", InvokerSecondPassAnnotationVisitor.this.targets, data.className, srcName);
			} else {
				srcName = data.memberName;
				dstName = CommonUtility.addCamelPrefix(prefix, dstName);

				CommonUtility.emit(
						data.remapper, AnnotationType.METHOD, data.mapping,
						data.className, srcName, srcDesc, dstName);
			}
		}

		super.visitEnd();
	}
}
