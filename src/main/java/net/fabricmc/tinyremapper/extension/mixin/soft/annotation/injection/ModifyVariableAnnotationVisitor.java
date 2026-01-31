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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrLocal;
import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;
import net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo;

public class ModifyVariableAnnotationVisitor extends AnnotationNode {
	private final CommonData data;
	private final AnnotationVisitor delegate;
	private final List<String> targets;

	private final List<MemberInfo> methods = new ArrayList<>();

	public ModifyVariableAnnotationVisitor(CommonData data, AnnotationVisitor delegate, List<String> targets) {
		super(Constant.ASM_VERSION, Annotation.MODIFY_VARIABLE);
		this.data = Objects.requireNonNull(data);
		this.delegate = Objects.requireNonNull(delegate);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor av = super.visitArray(name);

		if (name.equals(AnnotationElement.METHOD)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, av) {
				@Override
				public void visit(String name, Object value) {
					MemberInfo info = MemberInfo.parse(Objects.requireNonNull((String) value).replaceAll("\\s", ""));

					if (info != null && (info.getOwner().isEmpty() || ModifyVariableAnnotationVisitor.this.targets.contains(info.getOwner()))) {
						ModifyVariableAnnotationVisitor.this.methods.add(info);
					}

					super.visit(name, value);
				}
			};
		}

		return av;
	}

	@Override
	public void visitEnd() {
		this.accept(new ModifyVariableSecondPassAnnotationVisitor(this.data, this.delegate, this.targets, this.methods));

		super.visitEnd();
	}

	private static class ModifyVariableSecondPassAnnotationVisitor extends CommonInjectionAnnotationVisitor {
		private final List<MemberInfo> methods;
		private final List<TrClass> targets;

		ModifyVariableSecondPassAnnotationVisitor(CommonData data, AnnotationVisitor delegate, List<String> targets, List<MemberInfo> methods) {
			super(data, delegate, targets);
			this.methods = methods;
			this.targets = Objects.requireNonNull(targets).stream()
					.map(data.resolver::resolveClass)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.collect(Collectors.toList());
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			AnnotationVisitor av = super.visitArray(name);

			if (name.equals(AnnotationElement.NAME)) {
				return new AnnotationVisitor(Constant.ASM_VERSION, av) {
					@Override
					public void visit(String name, Object value) {
						String localName = Objects.requireNonNull((String) value).replaceAll("\\s", "");

						List<String> collection = targets.stream()
								.flatMap(target -> methods.stream().map(info -> resolvePartial(target, info.getName(), info.getDesc())))
								.filter(Optional::isPresent)
								.map(Optional::get)
								.map(m -> {
									TrLocal[] localVariables = m.getLocals();

									if (localVariables == null || localVariables.length == 0) {
										return localName;
									}

									Map<String, Integer> lvtName2Index = new HashMap<>();

									for (TrLocal variable : localVariables) {
										if (!lvtName2Index.containsKey(variable.getName())) {
											lvtName2Index.put(variable.getName(), variable.getIndex());
										} else {
											lvtName2Index.put(variable.getName(), -1); // TODO actually generate lvt for injection points, currently only handles unique names
										}
									}

									if (!lvtName2Index.containsKey(localName)) {
										return localName;
									}

									int lvIndex = lvtName2Index.get(localName);

									if (lvIndex < 0) {
										return localName;
									}

									return data.mapper.asTrRemapper().mapMethodArg(m.getOwner().getName(), m.getName(), m.getDesc(), lvIndex, localName);
								})
								.distinct().collect(Collectors.toList());

						if (collection.size() > 1) {
							data.getLogger().error(Message.CONFLICT_MAPPING, localName, collection);
						} else if (collection.isEmpty()) {
							data.getLogger().warn(Message.NO_MAPPING_NON_RECURSIVE, localName, targets);
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
