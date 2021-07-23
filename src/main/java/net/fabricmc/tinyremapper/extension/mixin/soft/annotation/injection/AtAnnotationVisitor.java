package net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.AnnotationVisitor;

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

/**
 * {@code @At} require fully-qualified {@link net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo} unless
 * {@code value = NEW}, in which case a special set of rule applies.
 */
class AtAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonData data;
	private final AnnotationVisitor delegate;

	private String value;

	AtAnnotationVisitor(CommonData data, AnnotationVisitor delegate, boolean remap) {
		super(Annotation.AT, remap);

		this.data = Objects.requireNonNull(data);
		this.delegate = Objects.requireNonNull(delegate);
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.VALUE)) {
			this.value = Objects.requireNonNull((String) value);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (remap) {
			this.accept(new AtSecondPassAnnotationVisitor(data, delegate, value));
		} else {
			this.accept(delegate);
		}

		super.visitEnd();
	}

	private static class AtMethodMappable implements IMappable<MemberInfo> {
		private final CommonData data;
		private final MemberInfo info;

		AtMethodMappable(CommonData data, MemberInfo info) {
			this.data = Objects.requireNonNull(data);
			this.info = Objects.requireNonNull(info);
		}

		@Override
		public MemberInfo result() {
			if (!info.isFullyQualified()) {
				data.logger.warn(info + " is not fully qualified.");
				return info;
			}

			Resolver resolver = new Resolver(data.logger);
			MapUtility mapper = new MapUtility(data.remapper, data.logger);

			Optional<TrMember> resolved = resolver.resolve(data.environment.getClass(info.getOwner()), info.getName(), info.getDesc(), Resolver.FLAG_UNIQUE | Resolver.FLAG_RECURSIVE);

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

	private static class AtConstructorMappable implements IMappable<MemberInfo> {
		private final CommonData data;
		private final MemberInfo info;

		AtConstructorMappable(CommonData data, MemberInfo info) {
			this.data = Objects.requireNonNull(data);
			this.info = Objects.requireNonNull(info);
		}

		@Override
		public MemberInfo result() {
			if (info.getDesc().isEmpty()) {
				// remap owner only
				return new MemberInfo(data.remapper.map(info.getOwner()), info.getName(), info.getQuantifier(), "");
			} else if (info.getDesc().endsWith(")V")) {
				// remap owner and desc
				return new MemberInfo(data.remapper.map(info.getOwner()), info.getName(), info.getQuantifier(), data.remapper.mapMethodDesc(info.getDesc()));
			} else {
				// remap desc only
				return new MemberInfo(info.getOwner(), info.getName(), info.getQuantifier(), data.remapper.mapMethodDesc(info.getDesc()));
			}
		}
	}

	private static class AtSecondPassAnnotationVisitor extends AnnotationVisitor {
		private final CommonData data;
		private final String value;

		AtSecondPassAnnotationVisitor(CommonData data, AnnotationVisitor delegate, String value) {
			super(Constant.ASM_VERSION, delegate);

			this.data = Objects.requireNonNull(data);
			this.value = Objects.requireNonNull(value);
		}

		@Override
		public void visit(String name, Object value) {
			if (name.equals(AnnotationElement.TARGET)) {
				Optional<MemberInfo> info = Optional.ofNullable(MemberInfo.parse(Objects.requireNonNull((String) value).replaceAll("\\s", "")));

				if (value.equals("NEW")) {
					value = info.map(i -> new AtConstructorMappable(data, i).result().toString()).orElse((String) value);
				} else {
					value = info.map(i -> new AtMethodMappable(data, i).result().toString()).orElse((String) value);
				}
			}

			super.visit(name, value);
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			AnnotationVisitor av = super.visitArray(name);

			if (name.equals(AnnotationElement.ARGS) && value.equals("NEW")) {
				final String prefix = "class=";

				av = new AnnotationVisitor(Constant.ASM_VERSION, av) {
					@Override
					public void visit(String name, Object value) {
						String argument = Objects.requireNonNull((String) value);

						if (argument.startsWith(prefix)) {
							Optional<MemberInfo> info = Optional.ofNullable(MemberInfo.parse(argument.substring(prefix.length()).replaceAll("\\s", "")));

							value = prefix + info.map(i -> new AtConstructorMappable(data, i).result().toString()).orElse((String) value);
						}

						super.visit(name, value);
					}
				};
			}

			return av;
		}
	}
}
