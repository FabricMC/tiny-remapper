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

package net.fabricmc.tinyremapper.extension.mixin.hard.annotation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.CamelPrefixString;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.ConvertibleMappable;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.IConvertibleString;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 * If after strip the prefix, all characters are UPPER_CASE, then
 * do not lower the first character of the remaining part.
 */
public class AccessorAnnotationVisitor extends AnnotationVisitor {
	private final List<Consumer<CommonData>> tasks;
	private final MxMember method;
	private final List<String> targets;

	private boolean remap;
	private boolean isSoftTarget;

	public AccessorAnnotationVisitor(List<Consumer<CommonData>> tasks, AnnotationVisitor delegate, MxMember method, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);

		this.tasks = Objects.requireNonNull(tasks);
		this.method = Objects.requireNonNull(method);
		this.targets = Objects.requireNonNull(targets);

		this.remap = remap;
		this.isSoftTarget = false;
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP)) {
			remap = Objects.requireNonNull((Boolean) value);
		} else if (name.equals(AnnotationElement.VALUE)) {
			isSoftTarget = true;
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (remap && !isSoftTarget) {
			tasks.add(data -> new AccessorMappable(data, method, targets).result());
		}

		super.visitEnd();
	}

	private static class AccessorMappable extends ConvertibleMappable {
		private final String prefix;
		private final String fieldDesc;

		private static final Pattern GETTER_PATTERN = Pattern.compile("(?<=\\(\\)).*");
		private static final Pattern SETTER_PATTERN = Pattern.compile("(?<=\\().*(?=\\)V)");

		AccessorMappable(CommonData data, MxMember self, Collection<String> targets) {
			super(data, self, targets);

			if (self.getName().startsWith("get")) {
				this.prefix = "get";
			} else if (self.getName().startsWith("set")) {
				this.prefix = "set";
			} else if (self.getName().startsWith("is")) {
				this.prefix = "is";
			} else {
				throw new RuntimeException(String.format("%s does not start with get, set or is.", self.getName()));
			}

			Matcher getterMatcher = GETTER_PATTERN.matcher(self.getDesc());
			Matcher setterMatcher = SETTER_PATTERN.matcher(self.getDesc());

			if (getterMatcher.find()) {
				this.fieldDesc = getterMatcher.group();
			} else if (setterMatcher.find()) {
				this.fieldDesc = setterMatcher.group();
			} else {
				throw new RuntimeException(String.format("%s is not getter or setter descriptor", self.getDesc()));
			}
		}

		@Override
		protected IConvertibleString getName() {
			return new CamelPrefixString(prefix, self.getName());
		}

		@Override
		protected String getDesc() {
			return fieldDesc;
		}
	}
}
