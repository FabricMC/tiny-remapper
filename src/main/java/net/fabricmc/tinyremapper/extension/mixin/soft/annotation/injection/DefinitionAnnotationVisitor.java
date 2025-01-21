/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2025, FabricMC
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

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo;

public class DefinitionAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;

	public DefinitionAnnotationVisitor(CommonData data, AnnotationVisitor delegate) {
		super(Constant.ASM_VERSION, Objects.requireNonNull(delegate));

		this.data = Objects.requireNonNull(data);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor av = super.visitArray(name);
		switch (name) {
		case AnnotationElement.DEFINITION_METHOD:
		case AnnotationElement.DEFINITION_FIELD:
			return new MemberRemappingVisitor(data, av);
		}

		return av;
	}

	private static class MemberRemappingVisitor extends AnnotationVisitor {
		private final CommonData data;

		MemberRemappingVisitor(CommonData data, AnnotationVisitor delegate) {
			super(Constant.ASM_VERSION, Objects.requireNonNull(delegate));

			this.data = Objects.requireNonNull(data);
		}

		@Override
		public void visit(String name, Object value) {
			MemberInfo info = MemberInfo.parse(Objects.requireNonNull((String) value));

			if (info != null) {
				value = new AtMemberMappable(data, info).result().toString();
			}

			super.visit(name, value);
		}
	}
}
