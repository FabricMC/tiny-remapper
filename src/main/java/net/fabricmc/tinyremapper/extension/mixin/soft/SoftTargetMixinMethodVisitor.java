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
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.InjectAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyArgAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyArgsAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyConstantAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.ModifyVariableAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection.RedirectAnnotationVisitor;

class SoftTargetMixinMethodVisitor extends MethodVisitor {
	private final CommonData data;
	private final MxMember method;

	private final boolean remap;
	private final List<String> targets;

	SoftTargetMixinMethodVisitor(CommonData data, MethodVisitor delegate, MxMember method, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.method = Objects.requireNonNull(method);

		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

		if (Annotation.ACCESSOR.equals(descriptor)) {
			av = new AccessorAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.INVOKER.equals(descriptor)) {
			av = new InvokerAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.INJECT.equals(descriptor)) {
			av = new InjectAnnotationVisitor(data, av, remap, targets);
		} else if (Annotation.MODIFY_ARG.equals(descriptor)) {
			av = new ModifyArgAnnotationVisitor(data, av, remap, targets);
		} else if (Annotation.MODIFY_ARGS.equals(descriptor)) {
			av = new ModifyArgsAnnotationVisitor(data, av, remap, targets);
		} else if (Annotation.MODIFY_CONSTANT.equals(descriptor)) {
			av = new ModifyConstantAnnotationVisitor(data, av, remap, targets);
		} else if (Annotation.MODIFY_VARIABLE.equals(descriptor)) {
			av = new ModifyVariableAnnotationVisitor(data, av, remap, targets);
		} else if (Annotation.REDIRECT.equals(descriptor)) {
			av = new RedirectAnnotationVisitor(data, av, remap, targets);
		}

		return av;
	}
}
