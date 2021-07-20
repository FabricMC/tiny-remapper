package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;
import net.fabricmc.tinyremapper.extension.mixin.annotation.common.FirstPassAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.data.MemberInfo;

public class AtAnnotationVisitor extends AtFirstPassAnnotationVisitor {
	AtAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(data, remap, targets);
	}
}

/**
 * Required orders: [{@code value} | {@code remap}] [{@code args} | {@code target} | {@code desc}]
 * <p>Pass 1: read value & remap</p>
 * <p>Pass 2: remap target & desc; if value=NEW, then also remap args.</p>
 */
class AtFirstPassAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonDataHolder data;
	private final List<String> targets;

	private String value;

	AtFirstPassAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Annotation.ACCESSOR, remap);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.VALUE.get())) {
			this.value = Objects.requireNonNull((String) value);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (remap) {
			this.accept(new AtSecondPassAnnotationVisitor(data, targets, value));
		} else {
			this.accept(data.delegate);
		}

		super.visitEnd();
	}
}

class AtSecondPassAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolder data;
	private final List<String> targets;
	private final String value;

	AtSecondPassAnnotationVisitor(CommonDataHolder data, List<String> targets, String value) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
		this.value = Objects.requireNonNull(value);
	}

	private String remapTargetSelector(String srcTargetSelector) {
		if (MemberInfo.isRegex(srcTargetSelector)) {
			return srcTargetSelector;
		} else {
			MemberInfo srcInfo = MemberInfo.parse(srcTargetSelector);
			MemberInfo dstInfo = CommonUtility.remap(data.remapper, data.environment, targets, srcInfo);

			if (srcInfo.name.equals(dstInfo.name) && !Constant.UNMAP_NAMES.contains(srcInfo.name)) {
				if (srcInfo.owner.isEmpty()) {
					Logger.remapFail("@At", targets, data.className, srcInfo.name);
				} else {
					Logger.remapFail("@At", Collections.singletonList(CommonUtility.classDescToName(srcInfo.owner)),
							data.className, srcInfo.name);
				}
			}

			return dstInfo.toString();
		}
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.TARGET.get())) {
			String srcName = Objects.requireNonNull((String) value).replaceAll("\\s", "");
			String dstName = remapTargetSelector(srcName);
			value = dstName;
		}

		super.visit(name, value);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String name, String descriptor) {
		AnnotationVisitor annotationVisitor = super.visitAnnotation(name, descriptor);

		if (name.equals(AnnotationElement.DESC.get())) {
			if (!descriptor.equals(Annotation.DESC)) {
				throw new RuntimeException("Unexpected annotation " + descriptor);
			}

			CommonDataHolder data = this.data.alterAnnotationVisitor(annotationVisitor);
			annotationVisitor = new DescAnnotationVisitor(data, true, targets);
		}

		return annotationVisitor;
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor annotationVisitor = super.visitArray(name);

		if (name.equals(AnnotationElement.ARGS.get()) && value.equals("NEW")) {
			final String prefix = "class=";
			annotationVisitor = new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
				@Override
				public void visit(String name, Object value) {
					String argument = Objects.requireNonNull((String) value);

					if (argument.startsWith(prefix)) {
						String srcName = argument.substring(prefix.length());
						String dstName = remapTargetSelector(srcName);

						if (srcName.equals(dstName)) {
							Logger.remapFail("@At", argument, data.className);
						}

						value = prefix + dstName;
					}

					super.visit(name, value);
				}
			};
		}

		return annotationVisitor;
	}
}
