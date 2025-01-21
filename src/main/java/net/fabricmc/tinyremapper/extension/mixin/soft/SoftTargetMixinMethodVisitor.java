/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, 2023, FabricMC
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

package net.fabricmc.tinyremapper.extension.mixin.soft;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.AccessorAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.InvokerAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.DefinitionAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.DefinitionsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.InjectAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyArgAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyArgsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyConstantAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyExpressionValueAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyReceiverAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyReturnValueAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyVariableAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.RedirectAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.WrapMethodAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.WrapOperationAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.WrapWithConditionAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.WrapWithConditionV2AnnotationVisitor;

class SoftTargetMixinMethodVisitor extends MethodVisitor {
	private final CommonData data;
	private final MxMember method;

	private final List<String> targets;

	SoftTargetMixinMethodVisitor(CommonData data, MethodVisitor delegate, MxMember method, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.method = Objects.requireNonNull(method);

		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

		switch (descriptor) {
		case Annotation.ACCESSOR:
			return new AccessorAnnotationVisitor(data, av, method, targets);
		case Annotation.INVOKER:
			return new InvokerAnnotationVisitor(data, av, method, targets);
		case Annotation.INJECT:
			return new InjectAnnotationVisitor(data, av, targets);
		case Annotation.MODIFY_ARG:
			return new ModifyArgAnnotationVisitor(data, av, targets);
		case Annotation.MODIFY_ARGS:
			return new ModifyArgsAnnotationVisitor(data, av, targets);
		case Annotation.MODIFY_CONSTANT:
			return new ModifyConstantAnnotationVisitor(data, av, targets);
		case Annotation.MODIFY_VARIABLE:
			return new ModifyVariableAnnotationVisitor(data, av, targets);
		case Annotation.REDIRECT:
			return new RedirectAnnotationVisitor(data, av, targets);
		case Annotation.MIXIN_EXTRAS_MODIFY_EXPRESSION_VALUE:
			return new ModifyExpressionValueAnnotationVisitor(data, av, targets);
		case Annotation.MIXIN_EXTRAS_MODIFY_RECEIVER:
			return new ModifyReceiverAnnotationVisitor(data, av, targets);
		case Annotation.MIXIN_EXTRAS_MODIFY_RETURN_VALUE:
			return new ModifyReturnValueAnnotationVisitor(data, av, targets);
		case Annotation.MIXIN_EXTRAS_WRAP_METHOD:
			return new WrapMethodAnnotationVisitor(data, av, targets);
		case Annotation.MIXIN_EXTRAS_WRAP_OPERATION:
			return new WrapOperationAnnotationVisitor(data, av, targets);
		case Annotation.MIXIN_EXTRAS_WRAP_WITH_CONDITION:
			return new WrapWithConditionAnnotationVisitor(data, av, targets);
		case Annotation.MIXIN_EXTRAS_WRAP_WITH_CONDITION_V2:
			return new WrapWithConditionV2AnnotationVisitor(data, av, targets);
		case Annotation.MIXIN_EXTRAS_DEFINITIONS:
			return new DefinitionsAnnotationVisitor(data, av);
		case Annotation.MIXIN_EXTRAS_DEFINITION:
			return new DefinitionAnnotationVisitor(data, av);
		}

		return av;
	}
}
