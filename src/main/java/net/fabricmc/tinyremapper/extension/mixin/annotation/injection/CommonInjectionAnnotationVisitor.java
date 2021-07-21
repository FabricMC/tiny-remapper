package net.fabricmc.tinyremapper.extension.mixin.annotation.injection;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;
import net.fabricmc.tinyremapper.extension.mixin.annotation.common.FirstPassAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.common.LoggerOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolderOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.data.MemberInfo;

class CommonInjectionAnnotationVisitor extends CommonInjectionFirstPassAnnotationVisitor {
	CommonInjectionAnnotationVisitor(String descriptor, CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(descriptor, data, remap, targets);
	}
}

class CommonInjectionFirstPassAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonDataHolderOld data;
	private final List<String> targets;

	CommonInjectionFirstPassAnnotationVisitor(
			String descriptor, CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(descriptor, remap);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visitEnd() {
		// The second pass is needed regardless of remap, because it may have
		// children annotation need to remap.
		this.accept(new CommonInjectionSecondPassAnnotationVisitor(data, super.remap, targets));

		super.visitEnd();
	}
}

class CommonInjectionSecondPassAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolderOld data;
	private final List<String> targets;
	private final boolean remap;

	CommonInjectionSecondPassAnnotationVisitor(CommonDataHolderOld data, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
		this.remap = remap;
	}

	private String remapTargetSelector(String srcTargetSelector) {
		if (MemberInfo.isRegex(srcTargetSelector)) {
			return srcTargetSelector;
		} else {
			MemberInfo srcInfo = MemberInfo.parse(srcTargetSelector);
			MemberInfo dstInfo = CommonUtility.remap(data.remapper, data.environment, targets, srcInfo);

			if (srcInfo.name.equals(dstInfo.name) && !Constant.UNMAP_NAMES.contains(srcInfo.name)) {
				if (srcInfo.owner.isEmpty()) {
					LoggerOld.remapFail("@Inject, @Redirect or @Modify*", targets, data.className, srcInfo.name);
				} else {
					LoggerOld.remapFail("@Inject, @Redirect or @Modify*", Collections.singletonList(CommonUtility.classDescToName(srcInfo.owner)),
							data.className, srcInfo.name);
				}
			}

			return dstInfo.toString();
		}
	}

	// TODO: remap name in @ModifyVariable, how to do?
	@Override
	public AnnotationVisitor visitAnnotation(String name, String descriptor) {
		AnnotationVisitor annotationVisitor = super.visitAnnotation(name, descriptor);

		if (name.equals(AnnotationElement.AT.get())) {	// @ModifyArg, @ModifyArgs, @Redirect, @ModifyVariable
			if (!descriptor.equals(Annotation.AT)) {
				throw new RuntimeException("Unexpected annotation " + descriptor);
			}

			CommonDataHolderOld data = CommonInjectionSecondPassAnnotationVisitor.this.data.alterAnnotationVisitor(annotationVisitor);
			annotationVisitor = new AtAnnotationVisitor(data, remap, targets);
		} else if (name.equals(AnnotationElement.SLICE.get())) {	// @ModifyArg, @ModifyArgs, @Redirect, @ModifyVariable
			if (!descriptor.equals(Annotation.SLICE)) {
				throw new RuntimeException("Unexpected annotation " + descriptor);
			}

			CommonDataHolderOld data = CommonInjectionSecondPassAnnotationVisitor.this.data.alterAnnotationVisitor(annotationVisitor);
			annotationVisitor = new SliceAnnotationVisitor(data, remap, targets);
		}

		return annotationVisitor;
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor annotationVisitor = super.visitArray(name);

		if (name.equals(AnnotationElement.TARGET.get())) {		// All
			return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (!descriptor.equals(Annotation.DESC)) {
						throw new RuntimeException("Unexpected annotation " + descriptor);
					}

					AnnotationVisitor annotationVisitor1 = super.visitAnnotation(name, descriptor);
					CommonDataHolderOld data = CommonInjectionSecondPassAnnotationVisitor.this.data.alterAnnotationVisitor(annotationVisitor1);

					return new DescAnnotationVisitor(data, remap, targets);
				}
			};
		} else if (name.equals(AnnotationElement.METHOD.get())) {	// All
			return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
				@Override
				public void visit(String name, Object value) {
					if (remap) {
						String srcName = Objects.requireNonNull((String) value).replaceAll("\\s", "");
						String dstName = remapTargetSelector(srcName);
						value = dstName;
					}

					super.visit(name, value);
				}
			};
		} else if (name.equals(AnnotationElement.AT.get())) {	// @Inject
			return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (!descriptor.equals(Annotation.AT)) {
						throw new RuntimeException("Unexpected annotation " + descriptor);
					}

					AnnotationVisitor annotationVisitor1 = super.visitAnnotation(name, descriptor);
					CommonDataHolderOld data = CommonInjectionSecondPassAnnotationVisitor.this.data.alterAnnotationVisitor(annotationVisitor1);

					return new AtAnnotationVisitor(data, remap, targets);
				}
			};
		} else if (name.equals(AnnotationElement.SLICE.get())) {	// @Inject @ModifyConstant
			return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (!descriptor.equals(Annotation.SLICE)) {
						throw new RuntimeException("Unexpected annotation " + descriptor);
					}

					AnnotationVisitor annotationVisitor1 = super.visitAnnotation(name, descriptor);
					CommonDataHolderOld data = CommonInjectionSecondPassAnnotationVisitor.this.data.alterAnnotationVisitor(annotationVisitor1);

					return new SliceAnnotationVisitor(data, remap, targets);
				}
			};
		}

		return annotationVisitor;
	}
}
