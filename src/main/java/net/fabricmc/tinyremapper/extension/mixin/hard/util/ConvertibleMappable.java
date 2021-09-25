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

package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Pair;

public abstract class ConvertibleMappable extends HardTargetMappable {
	private final Collection<TrClass> targets;

	public ConvertibleMappable(CommonData data, MxMember self, Collection<String> targets) {
		super(data, self);

		this.targets = Objects.requireNonNull(targets).stream()
				.map(data.resolver::resolveClass)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
	}

	protected abstract IConvertibleString getName();
	protected abstract String getDesc();

	protected Stream<String> mapMultiTarget(IConvertibleString name, String desc) {
		return targets.stream()
				.map(target -> Pair.of(name, data.resolver.resolveMember(target, name.getConverted(), desc, ResolveUtility.FLAG_UNIQUE | ResolveUtility.FLAG_RECURSIVE)))
				.filter(x -> x.second().isPresent())
				.map(x -> Pair.of(x.first(), data.mapper.mapName(x.second().get())))
				.map(x -> x.first().getReverted(x.second()));
	}

	@Override
	protected Optional<String> getMappedName() {
		List<String> collection = mapMultiTarget(getName(), getDesc())
				.collect(Collectors.toList());

		if (collection.size() > 1) {
			data.logger.error(String.format(Message.CONFLICT_MAPPING, self.getName(), collection));
		} else if (collection.isEmpty()) {
			data.logger.warn(String.format(Message.NO_MAPPING_RECURSIVE, self.getName(), targets));
		}

		return collection.stream().findFirst();
	}
}
