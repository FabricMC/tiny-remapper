/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, 2023, FabricMC
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Pair;
import net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo;

/**
 * If the {@code method} element does not contain a name, then do not remap it; If the
 * {@code method} element has multiple matches (i.e. no desc), then the non-synthetic
 * method with the first occurrence in ASM will be remapped.
 */
class CommonInjectionAnnotationVisitor extends AnnotationVisitor {
	protected final CommonData data;
	protected final List<String> targets;

	CommonInjectionAnnotationVisitor(CommonData data, AnnotationVisitor delegate, List<String> targets) {
		super(Constant.ASM_VERSION, Objects.requireNonNull(delegate));

		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String name, String descriptor) {
		AnnotationVisitor av = super.visitAnnotation(name, descriptor);

		if (name.equals(AnnotationElement.AT)) {	// @ModifyArg, @ModifyArgs, @Redirect, @ModifyVariable
			if (!descriptor.equals(Annotation.AT)) {
				throw new RuntimeException("Unexpected annotation " + descriptor);
			}

			av = new AtAnnotationVisitor(data, av, targets);
		} else if (name.equals(AnnotationElement.SLICE)) {	// @ModifyArg, @ModifyArgs, @Redirect, @ModifyVariable
			if (!descriptor.equals(Annotation.SLICE)) {
				throw new RuntimeException("Unexpected annotation " + descriptor);
			}

			av = new SliceAnnotationVisitor(data, av, targets);
		}

		return av;
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor av = super.visitArray(name);

		if (name.equals(AnnotationElement.METHOD)) {	// All
			return new AnnotationVisitor(Constant.ASM_VERSION, av) {
				@Override
				public void visit(String name, Object value) {
					String string = Objects.requireNonNull((String) value);

					MemberInfo info = MemberInfo.parse(string.replaceAll("\\s", ""));

					if (info == null) {
						super.visit(name, value);
						return;
					}

					List<MemberInfo> resolved = new InjectMethodMappable(data, info, targets).result();
					if (resolved.isEmpty()) {
						throw new RuntimeException("InjectMethodMappable should never resolve to zero entries");
					}

					for (MemberInfo remappedInfo : resolved) {
						super.visit(name, remappedInfo.toString());
					}
				}
			};
		} else if (name.equals(AnnotationElement.TARGET)) {	// All
			return new AnnotationVisitor(Constant.ASM_VERSION, av) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (!descriptor.equals(Annotation.DESC)) {
						throw new RuntimeException("Unexpected annotation " + descriptor);
					}

					AnnotationVisitor av1 = super.visitAnnotation(name, descriptor);
					return new DescAnnotationVisitor(targets, data, av1, MemberType.METHOD);
				}
			};
		} else if (name.equals(AnnotationElement.AT)) {	// @Inject
			return new AnnotationVisitor(Constant.ASM_VERSION, av) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (!descriptor.equals(Annotation.AT)) {
						throw new RuntimeException("Unexpected annotation " + descriptor);
					}

					AnnotationVisitor av1 = super.visitAnnotation(name, descriptor);
					return new AtAnnotationVisitor(data, av1, targets);
				}
			};
		} else if (name.equals(AnnotationElement.SLICE)) {	// @Inject @ModifyConstant
			return new AnnotationVisitor(Constant.ASM_VERSION, av) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (!descriptor.equals(Annotation.SLICE)) {
						throw new RuntimeException("Unexpected annotation " + descriptor);
					}

					AnnotationVisitor av1 = super.visitAnnotation(name, descriptor);
					return new SliceAnnotationVisitor(data, av1, targets);
				}
			};
		}

		return av;
	}

	private static class InjectMethodMappable implements IMappable<List<MemberInfo>> {
		private final CommonData data;
		private final MemberInfo info;
		private final List<TrClass> targets;

		InjectMethodMappable(CommonData data, MemberInfo info, List<String> targets) {
			this.data = Objects.requireNonNull(data);
			this.info = Objects.requireNonNull(info);

			if (info.getOwner().isEmpty()) {
				this.targets = Objects.requireNonNull(targets).stream()
						.map(data.resolver::resolveClass)
						.filter(Optional::isPresent)
						.map(Optional::get)
						.collect(Collectors.toList());
			} else {
				this.targets = data.resolver.resolveClass(info.getOwner())
						.map(Collections::singletonList)
						.orElse(Collections.emptyList());
			}
		}

		private List<TrMethod> resolvePartials(TrClass owner, String name, String desc) {
			Objects.requireNonNull(owner);

			name = name.isEmpty() ? null : name;
			desc = desc.isEmpty() ? null : desc;

			Collection<TrMethod> col = owner.resolveMethods(name, desc, false, null, null);
			if (col instanceof List) {
				return (List<TrMethod>) col;
			} else {
				return new ArrayList<>(col);
			}
		}

		@Override
		public List<MemberInfo> result() {
			String mappedOwner = info.getOwner();
			if (!mappedOwner.isEmpty()) {
				mappedOwner = data.mapper.asTrRemapper().map(mappedOwner);
			}

			Pair<Integer, Integer> parsedQuantifier = parseQuantifier(data, info.getQuantifier());
			int quantifierMin = parsedQuantifier.first();
			int methodsPerTarget = parsedQuantifier.second();

			if (targets.isEmpty() || info.getName().isEmpty() || methodsPerTarget <= 0) {
				// Simple case when we can't find the specific method by name

				String desc = info.getDesc();
				if (!desc.isEmpty()) {
					desc = data.mapper.asTrRemapper().mapDesc(desc);
				}

				return Collections.singletonList(new MemberInfo(mappedOwner, info.getName(), info.getQuantifier(), desc));
			}

			// Step 1. Collect all methods we want to target

			Map<Pair<String, String>, Set<TrClass>> fullMethodToTarget = new HashMap<>();
			SortedMap<String, SortedSet<String>> namesToDesc = new TreeMap<>();

			for (TrClass target : targets) {
				List<TrMethod> methods = resolvePartials(target, info.getName(), info.getDesc());

				int matchedCount = Math.min(methods.size(), methodsPerTarget);
				for (int i = 0; i < matchedCount; i++) {
					TrMember method = methods.get(i);

					String mappedName = data.mapper.mapName(method);
					String mappedDesc = data.mapper.mapDesc(method);

					fullMethodToTarget.computeIfAbsent(Pair.of(mappedName, mappedDesc), k -> new HashSet<>()).add(target);
					namesToDesc.computeIfAbsent(mappedName, k -> new TreeSet<>()).add(mappedDesc);
				}
			}

			if (fullMethodToTarget.isEmpty()) {
				data.getLogger().warn(Message.NO_MAPPING_NON_RECURSIVE, info.toString(), targets);
				return Collections.singletonList(info);
			}

			// Step 2. Try adding methods
			// We need to avoid injecting into methods which weren't injected into in the source namespace
			// The canInject() functions check to make sure we aren't targeting something unwanted

			List<MemberInfo> list = new ArrayList<>();

			boolean explicitDesc = !info.getDesc().isEmpty();

			for (Map.Entry<String, SortedSet<String>> entry : namesToDesc.entrySet()) {
				String mappedName = entry.getKey();
				SortedSet<String> mappedDescriptors = entry.getValue();

				if (!explicitDesc && canInject(mappedName, methodsPerTarget, fullMethodToTarget)) { // Try to apply method name without descriptor if possible
					list.add(new MemberInfo(mappedOwner, mappedName, info.getQuantifier(), ""));
				} else {
					for (String mappedDesc : mappedDescriptors) {
						if (canInject(mappedName, mappedDesc, fullMethodToTarget)) {
							String quantifier = info.getQuantifier();
							if (!explicitDesc) {
								if (quantifierMin > 0) {
									data.getLogger().error(Message.UNSUPPORTED_QUANTIFIER_MIN, info.toString());
								}
								quantifier = "";
							}
							list.add(new MemberInfo(mappedOwner, mappedName, quantifier, mappedDesc));
						} else {
							data.getLogger().error(Message.MISSING_INJECT, info.toString(), mappedName, mappedDesc);
						}
					}
				}
			}

			if (list.isEmpty()) {
				return Collections.singletonList(info);
			}

			return list;
		}

		private boolean canInject(String mappedName, int methodsPerTarget, Map<Pair<String, String>, Set<TrClass>> fullMethodToTarget) {
			if (methodsPerTarget <= 0) {
				throw new IllegalArgumentException();
			}

			for (TrClass target : targets) {
				int toCheck = methodsPerTarget;

				for (TrMethod method : target.getMethods()) {
					String otherName = data.mapper.mapName(method);
					if (!otherName.equals(mappedName)) {
						continue;
					}

					String otherDesc = data.mapper.mapDesc(method);

					Pair<String, String> pair = Pair.of(otherName, otherDesc);
					Set<TrClass> validClasses = fullMethodToTarget.get(pair);
					if (validClasses == null || !validClasses.contains(target)) {
						return false;
					}

					// We only break if methodsPerTarget > 1 in order to disambiguate even when unnecessary due to implicit limit of 1
					// e.g. If targeting method foo in [foo, bar -> baz, baz], we want to disambiguate the baz even though we would be
					// targeting the correct method due to the max limit
					toCheck -= 1;
					if (toCheck <= 0 && methodsPerTarget > 1) {
						break;
					}
				}
			}

			return true;
		}

		private boolean canInject(String mappedName, String mappedDesc, Map<Pair<String, String>, Set<TrClass>> fullMethodToTarget) {
			Pair<String, String> pair = Pair.of(mappedName, mappedDesc);
			Set<TrClass> validClasses = fullMethodToTarget.get(pair);
			if (validClasses == null || validClasses.isEmpty()) {
				return false;
			}

			for (TrClass target : targets) {
				TrMethod method = target.getMethod(mappedName, mappedDesc);
				if (method != null && !validClasses.contains(target)) {
					return false;
				}
			}

			return true;
		}
	}

	// Code based on Mixin Quantifier parsing code
	// Copyright (c) SpongePowered <https://www.spongepowered.org>
	// Copyright (c) contributors
	// https://github.com/FabricMC/Mixin/blob/e4edb3afad347f7561acf6a9dd4a64f2aa479658/src/main/java/org/spongepowered/asm/util/Quantifier.java
	private static Pair<Integer, Integer> parseQuantifier(CommonData data, String quantifier) {
		if (quantifier == null || quantifier.isEmpty()) {
			return Pair.of(0, 1);
		}
		if (quantifier.equals("*")) {
			return Pair.of(0, Integer.MAX_VALUE);
		}
		if (quantifier.equals("+")) {
			return Pair.of(1, Integer.MAX_VALUE);
		}
		if (!quantifier.startsWith("{") || !quantifier.endsWith("}") || quantifier.length() < 3) {
			data.getLogger().error(Message.UNABLE_TO_PARSE_QUANTIFIER, quantifier);
			return Pair.of(0, 0);
		}

		String inner = quantifier.substring(1, quantifier.length() - 1).trim();
		if (inner.isEmpty()) {
			data.getLogger().error(Message.UNABLE_TO_PARSE_QUANTIFIER, quantifier);
			return Pair.of(0, 0);
		}

		String strMin = inner;
		String strMax = inner;

		int comma = inner.indexOf(',');
		if (comma > -1) {
			strMin = inner.substring(0, comma).trim();
			strMax = inner.substring(comma + 1).trim();
		}

		try {
			int min = !strMin.isEmpty() ? Integer.parseInt(strMin) : 0;
			int max = !strMax.isEmpty() ? Integer.parseInt(strMax) : Integer.MAX_VALUE;
			return Pair.of(min, Math.max(min, max));
		} catch (NumberFormatException ex) {
			data.getLogger().error(Message.UNABLE_TO_PARSE_QUANTIFIER, quantifier);
			return Pair.of(0, 0);
		}

	}



}
