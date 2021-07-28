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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.soft.util.NamedMappable;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 */
public class AccessorAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonData data;
	private final AnnotationVisitor delegate;
	private final MxMember method;

	private final List<String> targets;

	public AccessorAnnotationVisitor(CommonData data, AnnotationVisitor delegate, MxMember method, boolean remap, List<String> targets) {
		super(Annotation.ACCESSOR, remap);

		this.data = Objects.requireNonNull(data);
		this.delegate = Objects.requireNonNull(delegate);
		this.method = Objects.requireNonNull(method);

		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visitEnd() {
		if (super.remap) {
			this.accept(new AccessorSecondPassAnnotationVisitor(data, delegate, method, targets));
		} else {
			this.accept(delegate);
		}

		super.visitEnd();
	}

	private static class AccessorSecondPassAnnotationVisitor extends AnnotationVisitor {
		private final CommonData data;
		private final List<String> targets;
		private final String fieldDesc;

		private static final Pattern GETTER_PATTERN = Pattern.compile("(?<=\\(\\)).*");
		private static final Pattern SETTER_PATTERN = Pattern.compile("(?<=\\().*(?=\\)V)");

		AccessorSecondPassAnnotationVisitor(CommonData data, AnnotationVisitor delegate, MxMember method, List<String> targets) {
			super(Constant.ASM_VERSION, delegate);

			this.data = Objects.requireNonNull(data);
			this.targets = Objects.requireNonNull(targets);

			Matcher getterMatcher = GETTER_PATTERN.matcher(method.getDesc());
			Matcher setterMatcher = SETTER_PATTERN.matcher(method.getDesc());

			if (getterMatcher.find()) {
				this.fieldDesc = getterMatcher.group();
			} else if (setterMatcher.find()) {
				this.fieldDesc = setterMatcher.group();
			} else {
				throw new RuntimeException(method.getDesc() + " is not getter or setter");
			}
		}

		@Override
		public void visit(String name, Object value) {
			if (name.equals(AnnotationElement.VALUE)) {
				String fieldName = Objects.requireNonNull((String) value);

				value = new NamedMappable(data, fieldName, fieldDesc, targets).result();
			}

			super.visit(name, value);
		}
	}
}
