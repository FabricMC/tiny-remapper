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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
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

					MemberInfo[] resolved = new InjectMethodMappable(data, info, targets).result();
					if (resolved.length == 0) {
						throw new RuntimeException("InjectMethodMappable should never resolve to zero entries");
					}

					for (MemberInfo memberInfos : resolved) {
						super.visit(name, memberInfos.toString());
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

	private static class InjectMethodMappable implements IMappable<MemberInfo[]> {
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

		private Optional<TrMember> resolvePartial(TrClass owner, String name, String desc) {
			Objects.requireNonNull(owner);

			name = name.isEmpty() ? null : name;
			desc = desc.isEmpty() ? null : desc;

			return data.resolver.resolveMethod(owner, name, desc, ResolveUtility.FLAG_FIRST).map(m -> m);
		}

		private Collection<TrMethod> resolvePartials(TrClass owner, String name, String desc) {
			Objects.requireNonNull(owner);

			name = name.isEmpty() ? null : name;
			desc = desc.isEmpty() ? null : desc;

			return owner.resolveMethods(name, desc, false, null, null);
		}

		@Override
		public MemberInfo[] result() {
			if (info.getQuantifier().equals("*")) {
				return this.wildcardResult();
			} else {
				return new MemberInfo[] { singleResult() };
			}
		}

		private MemberInfo[] wildcardResult() {
			// Special case to remap the desc of wildcards without a name, such as `*()Lcom/example/ClassName;`
			if (info.getName().isEmpty() && !info.getDesc().isEmpty()) {
				return new MemberInfo[] {
					new MemberInfo(data.mapper.asTrRemapper().map(info.getOwner()), info.getName(), "*", data.mapper.asTrRemapper().mapDesc(info.getDesc()))
				};
			}

			if (targets.isEmpty() || info.getName().isEmpty()) {
				return new MemberInfo[] { info };
			}

			List<Pair<String, String>> collection = targets.stream()
			   .flatMap(target -> resolvePartials(target, info.getName(), info.getDesc()).stream())
			   .map(m -> Pair.of(data.mapper.mapName(m), data.mapper.mapDesc(m)))
			   .distinct()
			   .collect(Collectors.toList());

			if (collection.isEmpty()) {
				data.getLogger().warn(Message.NO_MAPPING_NON_RECURSIVE, info.getName(), targets);
				return new MemberInfo[] { info };
			}

			Map<String, Set<String>> descriptorsForName = new TreeMap<>();
			for (Pair<String, String> pair : collection) {
				descriptorsForName.computeIfAbsent(pair.first(), k -> new TreeSet<>()).add(pair.second());
			}

			List<MemberInfo> finalMembers = new ArrayList<>();

			if (info.getDesc().isEmpty()) {
				// If the descriptor was omitted in the input, we want to omit the descriptor in the output as well
				// However, we can only do this if all the methods in the source namespace
				// are exactly matched in the target namespace

				for (Map.Entry<String, Set<String>> entry : descriptorsForName.entrySet()) {
					String mappedName = entry.getKey();
					Set<String> mappedDescriptors = entry.getValue();

					Set<String> allDescriptorsInTargets = new HashSet<>();

					for (TrClass target : targets) {
						for (TrMethod method : target.getMethods()) {
							String otherName = data.mapper.mapName(method);
							if (otherName.equals(mappedName)) {
								allDescriptorsInTargets.add(data.mapper.mapDesc(method));
							}
						}
					}

					if (allDescriptorsInTargets.equals(mappedDescriptors)) {
						finalMembers.add(new MemberInfo(data.mapper.asTrRemapper().map(info.getOwner()), mappedName, "*", ""));
					} else {
						for (String mappedDesc : mappedDescriptors) {
							finalMembers.add(new MemberInfo(data.mapper.asTrRemapper().map(info.getOwner()), mappedName, "*", mappedDesc));
						}
					}
				}
			} else {
				for (Map.Entry<String, Set<String>> entry : descriptorsForName.entrySet()) {
					String mappedName = entry.getKey();
					Set<String> mappedDescriptors = entry.getValue();

					for (String mappedDesc : mappedDescriptors) {
						finalMembers.add(new MemberInfo(data.mapper.asTrRemapper().map(info.getOwner()), mappedName, "*", mappedDesc));
					}
				}
			}

			return finalMembers.toArray(new MemberInfo[0]);
		}

		private MemberInfo singleResult() {
			if (targets.isEmpty() || info.getName().isEmpty()) {
				return info;
			}

			List<Pair<String, String>> collection = targets.stream()
			   .map(target -> resolvePartial(target, info.getName(), info.getDesc()))
			   .filter(Optional::isPresent)
			   .map(Optional::get)
			   .map(m -> Pair.of(data.mapper.mapName(m), data.mapper.mapDesc(m)))
			   .distinct()
			   .collect(Collectors.toList());

			if (collection.size() > 1) {
				data.getLogger().error(Message.CONFLICT_MAPPING, info.getName(), collection);
			} else if (collection.isEmpty()) {
				data.getLogger().warn(Message.NO_MAPPING_NON_RECURSIVE, info.getName(), targets);
				return info;
			}

			Pair<String, String> pair = collection.get(0);
			String mappedName = pair.first();

			boolean useDescriptor = !info.getDesc().isEmpty() || isNameAmbiguous(mappedName, pair.second());
			String desc = useDescriptor ? pair.second() : "";

			return new MemberInfo(data.mapper.asTrRemapper().map(info.getOwner()), mappedName, info.getQuantifier(), desc);
		}

		private boolean isNameAmbiguous(String mappedName, String mappedDesc) {
			// Try to find a method with the same name, but a different descriptor

			for (TrClass target : targets) {
				for (TrMethod method : target.getMethods()) {
					String otherName = data.mapper.mapName(method);
					if (otherName.equals(mappedName)) { // Same name
						String otherDesc = data.mapper.mapDesc(method);
						if (!otherDesc.equals(mappedDesc)) { // Different descriptor
							return true;
						}
					}
				}
			}

			return false;
		}
	}
}
