package net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.Resolver;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.FirstPassAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo;

class CommonInjectionAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonData data;
	private final AnnotationVisitor delegate;
	private final TrMember method;
	private final List<TrClass> targets;

	CommonInjectionAnnotationVisitor(String descriptor, CommonData data, AnnotationVisitor delegate, TrMember method, boolean remap, List<TrClass> targets) {
		super(descriptor, remap);

		this.data = Objects.requireNonNull(data);
		this.delegate = Objects.requireNonNull(delegate);
		this.method = Objects.requireNonNull(method);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visitEnd() {
		// The second pass is needed regardless of remap, because it may have
		// children annotation need to remap.
		this.accept(new CommonInjectionSecondPassAnnotationVisitor(data, delegate, method, remap, targets));

		super.visitEnd();
	}

	private static class InjectMethodMappable implements IMappable<MemberInfo> {
		private final CommonData data;
		private final MemberInfo info;
		private final List<TrClass> targets;

		InjectMethodMappable(CommonData data, MemberInfo info, List<TrClass> targets) {
			this.data = Objects.requireNonNull(data);
			this.info = Objects.requireNonNull(info);
			this.targets = Objects.requireNonNull(targets);
		}

		@Override
		public MemberInfo result() {
			if (!info.isFullyQualified()) {
				return info;
			}

			Resolver resolver = new Resolver(data.environment, data.logger);
			MapUtility mapper = new MapUtility(data.remapper, data.logger);

			Optional<TrMember> resolved = resolver.resolve(info.getOwner(), info.getName(), info.getDesc(), Resolver.FLAG_UNIQUE | Resolver.FLAG_RECURSIVE);
			if (resolved.isPresent()) {
				String newOwner = data.remapper.map(info.getOwner());
				String newName = mapper.map(resolved.get());
				String newDesc = mapper.mapDesc(resolved.get());

				return new MemberInfo(newOwner, newName, info.getQuantifier(), newDesc);
			} else {
				data.logger.error("Cannot resolve for target selector " + info);
				return info;
			}
		}
	}

	private static class CommonInjectionSecondPassAnnotationVisitor extends AnnotationVisitor {
		private final CommonData data;
		private final TrMember method;

		private final boolean remap;
		private final List<TrClass> targets;

		CommonInjectionSecondPassAnnotationVisitor(CommonData data, AnnotationVisitor delegate, TrMember method, boolean remap, List<TrClass> targets) {
			super(Constant.ASM_VERSION, delegate);

			this.data = Objects.requireNonNull(data);
			this.method = Objects.requireNonNull(method);

			this.targets = Objects.requireNonNull(targets);
			this.remap = remap;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			AnnotationVisitor av = super.visitAnnotation(name, descriptor);

			if (name.equals(AnnotationElement.AT)) {	// @ModifyArg, @ModifyArgs, @Redirect, @ModifyVariable
				if (!descriptor.equals(Annotation.AT)) {
					throw new RuntimeException("Unexpected annotation " + descriptor);
				}

				av = new AtAnnotationVisitor(data, av, remap);
			} else if (name.equals(AnnotationElement.SLICE)) {	// @ModifyArg, @ModifyArgs, @Redirect, @ModifyVariable
				if (!descriptor.equals(Annotation.SLICE)) {
					throw new RuntimeException("Unexpected annotation " + descriptor);
				}

				av = new SliceAnnotationVisitor(data, av, remap);
			}

			return av;
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			AnnotationVisitor annotationVisitor = super.visitArray(name);

			if (name.equals(AnnotationElement.METHOD)) {	// All
				return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
					@Override
					public void visit(String name, Object value) {
						if (remap) {
							// TODO: fix
//							String srcName = Objects.requireNonNull((String) value).replaceAll("\\s", "");
//							String dstName = remapTargetSelector(srcName);
//							value = dstName;
						}

						super.visit(name, value);
					}
				};
			} else if (name.equals(AnnotationElement.AT)) {	// @Inject
				return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
					@Override
					public AnnotationVisitor visitAnnotation(String name, String descriptor) {
						if (!descriptor.equals(Annotation.AT)) {
							throw new RuntimeException("Unexpected annotation " + descriptor);
						}

						AnnotationVisitor av1 = super.visitAnnotation(name, descriptor);
						return new AtAnnotationVisitor(data, av1, remap);
					}
				};
			} else if (name.equals(AnnotationElement.SLICE)) {	// @Inject @ModifyConstant
				return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
					@Override
					public AnnotationVisitor visitAnnotation(String name, String descriptor) {
						if (!descriptor.equals(Annotation.SLICE)) {
							throw new RuntimeException("Unexpected annotation " + descriptor);
						}

						AnnotationVisitor av1 = super.visitAnnotation(name, descriptor);
						return new SliceAnnotationVisitor(data, av1, remap);
					}
				};
			}

			return annotationVisitor;
		}
	}
}
