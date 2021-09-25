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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;

/**
 * Only collect information for other hard-target.
 */
public class MixinAnnotationVisitor extends AnnotationVisitor {
	private final AtomicBoolean remap0;
	private final List<String> targets;

	public MixinAnnotationVisitor(AnnotationVisitor delegate, AtomicBoolean remapOut, List<String> targetsOut) {
		super(Constant.ASM_VERSION, delegate);

		this.remap0 = Objects.requireNonNull(remapOut);
		this.targets = Objects.requireNonNull(targetsOut);

		this.remap0.set(true);	// default value is true.
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP)) {
			remap0.set(Objects.requireNonNull((Boolean) value));
		}

		super.visit(name, value);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor visitor = super.visitArray(name);

		if (name.equals(AnnotationElement.TARGETS)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, visitor) {
				@Override
				public void visit(String name, Object value) {
					String srcName = ((String) value).replaceAll("\\s", "").replace('.', '/');
					String dstName = srcName;

					MixinAnnotationVisitor.this.targets.add(srcName);

					value = dstName;
					super.visit(name, value);
				}
			};
		} else if (name.equals(AnnotationElement.VALUE)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, visitor) {
				@Override
				public void visit(String name, Object value) {
					Type srcType = Objects.requireNonNull((Type) value);

					MixinAnnotationVisitor.this.targets.add(srcType.getInternalName());

					super.visit(name, value);
				}
			};
		} else {
			return visitor;
		}
	}
}
