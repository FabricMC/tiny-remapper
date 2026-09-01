/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2026, FabricMC
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;
import net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo;
import net.fabricmc.tinyremapper.extension.mixin.soft.util.LocalsMapper;

public class SugarLocalAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final Set<MemberInfo> knownTargetMethods;

	public SugarLocalAnnotationVisitor(CommonData data, AnnotationVisitor delegate, Set<MemberInfo> knownTargetMethods) {
		super(Constant.ASM_VERSION, delegate);

		this.data = Objects.requireNonNull(data);
		this.knownTargetMethods = Objects.requireNonNull(knownTargetMethods);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor av = super.visitArray(name);

		if (name.equals(AnnotationElement.NAME)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, av) {
				@Override
				public void visit(String name, Object value) {
					String localName = Objects.requireNonNull((String) value);

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
}
