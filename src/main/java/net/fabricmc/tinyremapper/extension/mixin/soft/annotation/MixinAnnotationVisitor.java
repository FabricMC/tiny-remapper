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
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;

/**
 * Required order: {@code remap} [{@code value} | {@code targets}]
 * <p>Pass 1: read remap.</p>
 * <p>Pass 2: read targets & value; remap targets.</p>
 */
public class MixinAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonData data;
	private final AnnotationVisitor delegate;

	private final AtomicBoolean remap0;
	private final List<String> targets;

	public MixinAnnotationVisitor(CommonData data, AnnotationVisitor delegate, AtomicBoolean remapOut, List<String> targetsOut) {
		super(Annotation.MIXIN, true);
		this.data = Objects.requireNonNull(data);
		this.delegate = Objects.requireNonNull(delegate);

		this.remap0 = Objects.requireNonNull(remapOut);
		this.targets = Objects.requireNonNull(targetsOut);
	}

	@Override
	public void visitEnd() {
		this.remap0.set(super.remap);

		// The second pass is needed regardless of remap, because it may have
		// children annotation need to remap.
		this.accept(new MixinSecondPassAnnotationVisitor(this.data, this.delegate, super.remap, this.targets));

		super.visitEnd();
	}

	private static class MixinSecondPassAnnotationVisitor extends AnnotationVisitor {
		private final CommonData data;

		private final boolean remap;
		private final List<String> targets;

		MixinSecondPassAnnotationVisitor(CommonData data, AnnotationVisitor delegate, boolean remap, List<String> targetsOut) {
			super(Constant.ASM_VERSION, delegate);

			this.data = Objects.requireNonNull(data);

			this.remap = remap;
			this.targets = Objects.requireNonNull(targetsOut);
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

						MixinSecondPassAnnotationVisitor.this.targets.add(srcName);

						if (remap) {
							dstName = data.mapper.asTrRemapper().map(srcName);
						}

						value = dstName;
						super.visit(name, value);
					}
				};
			} else if (name.equals(AnnotationElement.VALUE)) {
				return new AnnotationVisitor(Constant.ASM_VERSION, visitor) {
					@Override
					public void visit(String name, Object value) {
						Type srcType = Objects.requireNonNull((Type) value);

						MixinSecondPassAnnotationVisitor.this.targets.add(srcType.getInternalName());

						super.visit(name, value);
					}
				};
			} else {
				return visitor;
			}
		}
	}
}
