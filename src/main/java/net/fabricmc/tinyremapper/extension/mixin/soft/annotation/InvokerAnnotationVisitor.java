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

package net.fabricmc.tinyremapper.extension.mixin.soft.annotation;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.soft.util.NamedMappable;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 */
public class InvokerAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final MxMember method;

	private final List<String> targets;

	private boolean isSoftTarget;

	public InvokerAnnotationVisitor(CommonData data, AnnotationVisitor delegate, MxMember method, List<String> targets) {
		super(Constant.ASM_VERSION, Objects.requireNonNull(delegate));

		this.data = Objects.requireNonNull(data);
		this.method = Objects.requireNonNull(method);

		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.VALUE)) {
			isSoftTarget = true;
			String methodName = Objects.requireNonNull((String) value);

			setAnnotationValue(methodName);
			return;
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (!isSoftTarget) {
			String inferredName = inferMethodName();

			if (inferredName != null) {
				setAnnotationValue(inferredName);
			}
		}

		super.visitEnd();
	}

	private void setAnnotationValue(String methodName) {
		super.visit(AnnotationElement.VALUE, new NamedMappable(data, methodName, method.getDesc(), targets).result());
	}

	private String inferMethodName() {
		if (method.getName().startsWith("new") || method.getName().startsWith("create")) {
			// The rest of the name isn't important, leave it as-is
			return null;
		}

		String prefix;

		if (method.getName().startsWith("call")) {
			prefix = "call";
		} else if (method.getName().startsWith("invoke")) {
			prefix = "invoke";
		} else {
			throw new RuntimeException(String.format("%s does not start with call or invoke.", method.getName()));
		}

		return StringUtility.removeCamelPrefix(prefix, method.getName());
	}
}
