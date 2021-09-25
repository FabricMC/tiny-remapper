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

package net.fabricmc.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;

public class SliceAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final boolean remap;

	public SliceAnnotationVisitor(CommonData data, AnnotationVisitor delegate, boolean remap) {
		super(Constant.ASM_VERSION, delegate);
		this.data = Objects.requireNonNull(data);
		this.remap = remap;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String name, String descriptor) {
		AnnotationVisitor av = super.visitAnnotation(name, descriptor);

		if (name.equals(AnnotationElement.FROM) || name.equals(AnnotationElement.TO)) {
			if (!descriptor.equals(Annotation.AT)) {
				throw new RuntimeException("Unexpected annotation " + descriptor);
			}

			av = new AtAnnotationVisitor(data, av, remap);
		}

		return av;
	}
}
