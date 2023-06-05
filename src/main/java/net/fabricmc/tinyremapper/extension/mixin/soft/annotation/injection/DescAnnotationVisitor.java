/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2023, FabricMC
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.api.TrField;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;

class DescAnnotationVisitor extends AnnotationVisitor {
	private final List<String> targets;
	private final CommonData data;
	private final MemberType expectedType;

	private List<Type> args;
	private Type owner;
	private Type ret;
	private String value;

	DescAnnotationVisitor(List<String> targets, CommonData data, AnnotationVisitor annotationVisitor, MemberType expectedType) {
		super(Constant.ASM_VERSION, annotationVisitor);
		this.targets = targets;
		this.data = data;
		this.expectedType = Objects.requireNonNull(expectedType);
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.OWNER)) {
			owner = Objects.requireNonNull((Type) value);
			super.visit(name, value);
		} else if (name.equals(AnnotationElement.RET)) {
			ret = Objects.requireNonNull((Type) value);
			super.visit(name, value);
		} else if (name.equals(AnnotationElement.VALUE)) {
			this.value = Objects.requireNonNull((String) value);
		} else {
			super.visit(name, value);
		}
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		if (name.equals(AnnotationElement.ARGS)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, super.visitArray(name)) {
				private final List<Type> argArray = new ArrayList<>();

				@Override
				public void visit(String name, Object value) {
					argArray.add(Objects.requireNonNull((Type) value));
				}

				@Override
				public void visitEnd() {
					args = Collections.unmodifiableList(argArray);
				}
			};
		} else {
			return super.visitArray(name);
		}
	}

	@Override
	public void visitEnd() {
		Objects.requireNonNull(value);
		List<String> potentialOwners = targets;

		if (owner != null) {
			potentialOwners = Collections.singletonList(owner.getInternalName());
		}

		if (expectedType == MemberType.METHOD) {
			String desc = "(";

			if (args != null) {
				for (Type arg : args) {
					desc += arg.getDescriptor();
				}
			}

			desc += ")";

			if (ret != null) {
				desc += ret.getDescriptor();
			} else {
				desc += "V";
			}

			// TODO We assume that we get 1:1 mappings, however it is possible (especially with multiple owners) that
			// 1:N mappings are produced. In that case multiple @Desc annotations need to be generated.
			// The exception is if the @Desc annot is located in an @At annotation, in which adding a @Desc
			// is not possible as the @Desc annot is singular.
			// HOWEVER it would still be possible to use "standard" regex target selectors.
			// HOWEVER in doing so we would no longer be able to match both fields and methods within a single selector.
			// In short all approaches are not ideal

			String proposedName = null;

			for (String owner: potentialOwners) {
				Optional<TrMethod> resolved = data.resolver.resolveMethod(owner, value, desc, ResolveUtility.FLAG_RECURSIVE | ResolveUtility.FLAG_UNIQUE);

				if (!resolved.isPresent()) {
					continue;
				}

				String remapped = data.mapper.mapName(resolved.get());

				if (proposedName == null) {
					proposedName = remapped;
				} else if (!proposedName.equals(remapped)) {
					data.logger.error(String.format(Message.MULTIPLE_MAPPING_CHOICES, value + desc, owner + "." + remapped + desc, proposedName + desc));
				}
			}

			if (proposedName != null) {
				super.visit("value", proposedName);
			} else {
				super.visit("value", value);
			}
		} else if (expectedType == MemberType.FIELD) {
			if (ret == null) {
				data.logger.warn(String.format(Message.NOT_FULLY_QUALIFIED, owner + "." + value));
				super.visit("value", value);
				super.visitEnd();
				return;
			}

			String proposedName = null;
			String desc = ret.getDescriptor();

			for (String owner: potentialOwners) {
				Optional<TrField> resolved = data.resolver.resolveField(owner, value, desc, ResolveUtility.FLAG_RECURSIVE | ResolveUtility.FLAG_UNIQUE);

				if (!resolved.isPresent()) {
					continue;
				}

				String remapped = data.mapper.mapName(resolved.get());

				if (proposedName == null) {
					proposedName = remapped;
				} else if (!proposedName.equals(remapped)) {
					data.logger.error(String.format(Message.MULTIPLE_MAPPING_CHOICES, value + desc, owner + "." + remapped + desc, proposedName + desc));
				}
			}

			if (proposedName != null) {
				super.visit("value", proposedName);
			} else {
				super.visit("value", value);
			}
		}

		super.visitEnd();
	}
}
