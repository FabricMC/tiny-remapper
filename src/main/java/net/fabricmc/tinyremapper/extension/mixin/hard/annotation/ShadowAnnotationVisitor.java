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

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.ConvertibleMappable;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.IConvertibleString;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.IdentityString;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.PrefixString;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 * If a prefix is detected, will only attempt to match the prefix-stripped name.
 */
public class ShadowAnnotationVisitor extends AnnotationVisitor {
	private final List<Consumer<CommonData>> tasks;
	private final MxMember member;
	private final List<String> targets;

	private boolean remap;
	private String prefix;

	public ShadowAnnotationVisitor(List<Consumer<CommonData>> tasks, AnnotationVisitor delegate, MxMember member, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);
		this.tasks = Objects.requireNonNull(tasks);
		this.member = Objects.requireNonNull(member);

		this.targets = Objects.requireNonNull(targets);
		this.remap = remap;
		this.prefix = "shadow$";
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP)) {
			remap = Objects.requireNonNull((Boolean) value);
		} else if (name.equals(AnnotationElement.PREFIX)) {
			prefix = Objects.requireNonNull((String) value);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (remap) {
			tasks.add(data -> new ShadowPrefixMappable(data, member, targets, prefix).result());
		}

		super.visitEnd();
	}

	private static class ShadowPrefixMappable extends ConvertibleMappable {
		private final String prefix;

		ShadowPrefixMappable(CommonData data, MxMember self, Collection<String> targets, String prefix) {
			super(data, self, targets);
			Objects.requireNonNull(prefix);

			this.prefix = self.getName().startsWith(prefix) ? prefix : "";
		}

		@Override
		protected IConvertibleString getName() {
			if (prefix.isEmpty()) {
				return new IdentityString(self.getName());
			} else {
				return new PrefixString(prefix, self.getName());
			}
		}

		@Override
		protected String getDesc() {
			return self.getDesc();
		}
	}
}
