/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, FabricMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper.extension.mixin.common.data;

public final class Annotation {
	// .*
	public static final String DEBUG = "Lorg/spongepowered/asm/mixin/Debug;";
	public static final String DYNAMIC = "Lorg/spongepowered/asm/mixin/Dynamic;";
	public static final String FINAL = "Lorg/spongepowered/asm/mixin/Final;";
	public static final String IMPLEMENTS = "Lorg/spongepowered/asm/mixin/Implements;";
	public static final String INTERFACE = "Lorg/spongepowered/asm/mixin/Interface;";
	public static final String INTRINSIC = "Lorg/spongepowered/asm/mixin/Intrinsic;";
	public static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	public static final String MUTABLE = "Lorg/spongepowered/asm/mixin/Mutable;";
	public static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
	public static final String PSEUDO = "Lorg/spongepowered/asm/mixin/Pseudo;";
	public static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	public static final String SOFT_OVERRIDE = "Lorg/spongepowered/asm/mixin/SoftOverride;";
	public static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
	// .gen.*
	public static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
	public static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	// .injection.*
	public static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	public static final String COERCE = "Lorg/spongepowered/asm/mixin/injection/Coerce;";
	public static final String CONSTANT = "Lorg/spongepowered/asm/mixin/injection/Constant;";
	public static final String DESC = "Lorg/spongepowered/asm/mixin/injection/Desc;";
	public static final String DESCRIPTORS = "Lorg/spongepowered/asm/mixin/injection/Descriptors;";
	public static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";
	public static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	public static final String AT_CODE = "Lorg/spongepowered/asm/mixin/injection/InjectionPoint/AtCode;";
	public static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
	public static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
	public static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
	public static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
	public static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	public static final String SLICE = "Lorg/spongepowered/asm/mixin/injection/Slice;";
	public static final String Surrogate = "Lorg/spongepowered/asm/mixin/injection/Surrogate;";
	// .injection.selectors.*
	public static final String SELECTOR_ID = "Lorg/spongepowered/asm/mixin/injection/selectors/ITargetSelectorDynamic/SelectorId;";
	public static final String SELECTOR_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/selectors/ITargetSelectorDynamic/SelectorAnnotation;";
	// .injection.struct.*
	public static final String ANNOTATION_TYPE = "Lorg/spongepowered/asm/mixin/injection/struct/InjectionInfo/AnnotationType;";
	public static final String HANDLER_PREFIX = "Lorg/spongepowered/asm/mixin/injection/struct/InjectionInfo/HandlerPrefix;";
	// .transformer.meta.*
	public static final String MIXIN_INNER = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinInner;";
	public static final String MIXIN_MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
	public static final String MIXIN_PROXY = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinProxy;";
	public static final String MIXIN_RENAMED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinRenamed;";
}
