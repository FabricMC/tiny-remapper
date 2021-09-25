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

package net.fabricmc.tinyremapper.extension.mixin.hard;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.AccessorAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.InvokerAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.OverwriteAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.ShadowAnnotationVisitor;

class HardTargetMixinMethodVisitor extends MethodVisitor {
	private final List<Consumer<CommonData>> data;
	private final MxMember method;

	private final boolean remap;
	private final List<String> targets;

	HardTargetMixinMethodVisitor(List<Consumer<CommonData>> data, MethodVisitor delegate, MxMember method, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.method = Objects.requireNonNull(method);

		this.remap = remap;
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

		if (Annotation.SHADOW.equals(descriptor)) {
			av = new ShadowAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.OVERWRITE.equals(descriptor)) {
			av = new OverwriteAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.ACCESSOR.equals(descriptor)) {
			av = new AccessorAnnotationVisitor(data, av, method, remap, targets);
		} else if (Annotation.INVOKER.equals(descriptor)) {
			av = new InvokerAnnotationVisitor(data, av, method, remap, targets);
		}

		return av;
	}
}
