package net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.Resolver;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Pair;
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
			this.targets = info.getOwner().isEmpty()
					? Objects.requireNonNull(targets) : Collections.singletonList(data.environment.getClass(info.getOwner()));
		}

		private Optional<TrMember> resolvePartial(TrClass owner, String name, String desc) {
			Objects.requireNonNull(owner);

			Resolver resolver = new Resolver(data.logger);

			if (name.isEmpty()) return resolver.resolveByDesc(owner, desc, Resolver.FLAG_FIRST | Resolver.FLAG_NON_SYN);
			if (desc.isEmpty()) return resolver.resolveByName(owner, name, MemberType.METHOD, Resolver.FLAG_FIRST | Resolver.FLAG_NON_SYN);
			return resolver.resolve(owner, name, desc, Resolver.FLAG_UNIQUE);
		}

		@Override
		public MemberInfo result() {
			if (info.getName().isEmpty() && info.getDesc().isEmpty()) {
				return new MemberInfo(data.remapper.map(info.getOwner()), info.getName(), info.getQuantifier(), info.getDesc());
			}

			MapUtility mapper = new MapUtility(data.remapper, data.logger);
			List<Pair<String, String>> collection = targets.stream()
					.map(target -> resolvePartial(target, info.getName(), info.getDesc()))
					.filter(Optional::isPresent)
					.map(Optional::get)
					.map(m -> Pair.of(mapper.map(m), mapper.mapDesc(m)))
					.distinct().collect(Collectors.toList());

			if (collection.size() > 1) {
				data.logger.error("Conflict mapping detected, " + info.getName() + " -> " + collection);
			} else if (collection.isEmpty()) {
				data.logger.error("Cannot remap " + info.getName() + " because it does not exists in any of the targets " + targets);
			}

			return collection.stream().findFirst()
					.map(pair -> new MemberInfo(data.remapper.map(info.getOwner()), pair.first(), info.getQuantifier(), pair.second()))
					.orElse(info);
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
			AnnotationVisitor av = super.visitArray(name);

			if (name.equals(AnnotationElement.METHOD)) {	// All
				return new AnnotationVisitor(Constant.ASM_VERSION, av) {
					@Override
					public void visit(String name, Object value) {
						if (remap) {
							Optional<MemberInfo> info = Optional.ofNullable(MemberInfo.parse(Objects.requireNonNull((String) value).replaceAll("\\s", "")));

							value = info.map(i -> new InjectMethodMappable(data, i, targets).result().toString()).orElse((String) value);
						}

						super.visit(name, value);
					}
				};
			} else if (name.equals(AnnotationElement.AT)) {	// @Inject
				return new AnnotationVisitor(Constant.ASM_VERSION, av) {
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
				return new AnnotationVisitor(Constant.ASM_VERSION, av) {
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

			return av;
		}
	}
}
