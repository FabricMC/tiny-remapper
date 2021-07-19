package net.fabricmc.tinyremapper.extension.mixin.data;

public enum Annotation {
	// .*
	DEBUG("Lorg/spongepowered/asm/mixin/Debug;"),
	DYNAMIC("Lorg/spongepowered/asm/mixin/Dynamic;"),
	FINAL("Lorg/spongepowered/asm/mixin/Final;"),
	IMPLEMENTS("Lorg/spongepowered/asm/mixin/Implements;"),
	INTERFACE("Lorg/spongepowered/asm/mixin/Interface;"),
	INTRINSIC("Lorg/spongepowered/asm/mixin/Intrinsic;"),
	MIXIN("Lorg/spongepowered/asm/mixin/Mixin;"),
	MUTABLE("Lorg/spongepowered/asm/mixin/Mutable;"),
	OVERWRITE("Lorg/spongepowered/asm/mixin/Overwrite;"),
	PSEUDO("Lorg/spongepowered/asm/mixin/Pseudo;"),
	SHADOW("Lorg/spongepowered/asm/mixin/Shadow;"),
	SOFT_OVERRIDE("Lorg/spongepowered/asm/mixin/SoftOverride;"),
	UNIQUE("Lorg/spongepowered/asm/mixin/Unique;"),
	// .gen.*
	ACCESSOR("Lorg/spongepowered/asm/mixin/gen/Accessor;"),
	INVOKER("Lorg/spongepowered/asm/mixin/gen/Invoker;"),
	// .injection.*
	AT("Lorg/spongepowered/asm/mixin/injection/At;"),
	COERCE("Lorg/spongepowered/asm/mixin/injection/Coerce;"),
	CONSTANT("Lorg/spongepowered/asm/mixin/injection/Constant;"),
	DESC("Lorg/spongepowered/asm/mixin/injection/Desc;"),
	DESCRIPTORS("Lorg/spongepowered/asm/mixin/injection/Descriptors;"),
	GROUP("Lorg/spongepowered/asm/mixin/injection/Group;"),
	INJECT("Lorg/spongepowered/asm/mixin/injection/Inject;"),
	AT_CODE("Lorg/spongepowered/asm/mixin/injection/InjectionPoint/AtCode;"),
	MODIFY_ARG("Lorg/spongepowered/asm/mixin/injection/ModifyArg;"),
	MODIFY_ARGS("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;"),
	MODIFY_CONSTANT("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;"),
	MODIFY_VARIABLE("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;"),
	REDIRECT("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
	SLICE("Lorg/spongepowered/asm/mixin/injection/Slice;"),
	Surrogate("Lorg/spongepowered/asm/mixin/injection/Surrogate;"),
	// .injection.selectors.*
	SELECTOR_ID("Lorg/spongepowered/asm/mixin/injection/selectors/ITargetSelectorDynamic/SelectorId;"),
	SELECTOR_ANNOTATION("Lorg/spongepowered/asm/mixin/injection/selectors/ITargetSelectorDynamic/SelectorAnnotation;"),
	// .injection.struct.*
	ANNOTATION_TYPE("Lorg/spongepowered/asm/mixin/injection/struct/InjectionInfo/AnnotationType;"),
	HANDLER_PREFIX("Lorg/spongepowered/asm/mixin/injection/struct/InjectionInfo/HandlerPrefix;"),
	// .transformer.meta.*
	MIXIN_INNER("Lorg/spongepowered/asm/mixin/transformer/meta/MixinInner;"),
	MIXIN_MERGED("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;"),
	MIXIN_PROXY("Lorg/spongepowered/asm/mixin/transformer/meta/MixinProxy;"),
	MIXIN_RENAMED("Lorg/spongepowered/asm/mixin/transformer/meta/MixinRenamed;");

	private final String descriptor;

	Annotation(String descriptor) {
		this.descriptor = descriptor;
	}

	public String get() {
		return descriptor;
	}
}
