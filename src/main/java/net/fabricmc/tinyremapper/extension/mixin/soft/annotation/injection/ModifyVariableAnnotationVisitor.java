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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;
import net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo;
import net.fabricmc.tinyremapper.extension.mixin.soft.util.LocalsMapper;

public class ModifyVariableAnnotationVisitor extends AnnotationNode {
	private final CommonData data;
	private final AnnotationVisitor delegate;
	private final List<String> targets;
	private final Set<MemberInfo> knownTargetMethods;

	private final List<String> rawMethods = new ArrayList<>();

	public ModifyVariableAnnotationVisitor(CommonData data, AnnotationVisitor delegate, List<String> targets, Set<MemberInfo> knownTargetMethods) {
		super(Constant.ASM_VERSION, Annotation.MODIFY_VARIABLE);
		this.data = Objects.requireNonNull(data);
		this.delegate = Objects.requireNonNull(delegate);
		this.targets = Objects.requireNonNull(targets);
		this.knownTargetMethods = Objects.requireNonNull(knownTargetMethods);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor av = super.visitArray(name);

		if (name.equals(AnnotationElement.METHOD)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, av) {
				@Override
				public void visit(String name, Object value) {
					if (value != null) {
						ModifyVariableAnnotationVisitor.this.rawMethods.add((String) value);
					}

					super.visit(name, value);
				}
			};
		}

		return av;
	}

	@Override
	public void visitEnd() {
		this.accept(new ModifyVariableSecondPassAnnotationVisitor(this.data, this.delegate, this.targets, this.rawMethods, this.knownTargetMethods));

		super.visitEnd();
	}

	private static class ModifyVariableSecondPassAnnotationVisitor extends CommonInjectionAnnotationVisitor {
		private final List<String> rawMethods;
		private final List<TrClass> targets;

		private boolean visitedMethods = false;

		ModifyVariableSecondPassAnnotationVisitor(CommonData data, AnnotationVisitor delegate, List<String> targets, List<String> rawMethods, Set<MemberInfo> knownTargetMethods) {
			super(data, delegate, targets, knownTargetMethods);
			this.rawMethods = rawMethods;
			this.targets = Objects.requireNonNull(targets).stream()
					.map(data.resolver::resolveClass)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.collect(Collectors.toList());
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			if (name.equals(AnnotationElement.METHOD)) {
				if (visitedMethods) {
					return null; // blackhole so we don't visit it twice
				} else {
					visitedMethods = true;
				}
			}

			if (name.equals(AnnotationElement.NAME)) {
				if (!visitedMethods) {
					// methods must be visited before names, so force it right now
					AnnotationVisitor annotationVisitor = this.visitArray(AnnotationElement.METHOD);

					// logic borrowed from AnnotationNode.accept
					if (annotationVisitor != null) {
						for (String rawMethod : this.rawMethods) {
							annotationVisitor.visit(null, rawMethod);
						}

						annotationVisitor.visitEnd();
					}

					visitedMethods = true;
				}
			}

			AnnotationVisitor av = super.visitArray(name);

			if (name.equals(AnnotationElement.NAME)) {
				return new AnnotationVisitor(Constant.ASM_VERSION, av) {
					@Override
					public void visit(String name, Object value) {
						String localName = Objects.requireNonNull((String) value).replaceAll("\\s", "");

						List<TrMethod> targetMethods = knownTargetMethods.stream()
								// we should already have a unique set of methods, so set FLAG_UNIQUE
								.map(memberInfo -> data.resolver.resolveMethod(memberInfo.getOwner(), memberInfo.getName(), memberInfo.getDesc(), ResolveUtility.FLAG_UNIQUE))
								.filter(Optional::isPresent)
								.map(Optional::get)
								.collect(Collectors.toList());

						List<String> collection = targetMethods.stream()
								.map(m -> LocalsMapper.mapLocal(data, m, localName))
								.sorted().distinct().collect(Collectors.toList());

						if (collection.size() > 1) {
							data.getLogger().error(Message.CONFLICT_MAPPING, localName, collection);
						} else if (collection.isEmpty()) {
							data.getLogger().warn(Message.NO_MAPPING_NON_RECURSIVE, localName, targetMethods);
						}

						super.visit(name, collection.stream().findFirst().orElse(localName));
					}
				};
			}

			return av;
		}

		private Optional<TrMethod> resolvePartial(TrClass owner, String name, String desc) {
			Objects.requireNonNull(owner);

			name = name.isEmpty() ? null : name;
			desc = desc.isEmpty() ? null : desc;

			return data.resolver.resolveMethod(owner, name, desc, ResolveUtility.FLAG_FIRST | ResolveUtility.FLAG_NON_SYN).map(m -> m);
		}
	}
}
