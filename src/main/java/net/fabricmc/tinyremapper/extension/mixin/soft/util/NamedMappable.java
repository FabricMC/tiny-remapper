package net.fabricmc.tinyremapper.extension.mixin.soft.util;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;

public class NamedMappable implements IMappable<String> {
	private final CommonData data;
	private final String name;
	private final String desc;
	private final Collection<TrClass> targets;

	public NamedMappable(CommonData data, String name, String desc, Collection<String> targets) {
		this.data = Objects.requireNonNull(data);
		this.name = Objects.requireNonNull(name);
		this.desc = Objects.requireNonNull(desc);
		this.targets = Objects.requireNonNull(targets).stream()
				.map(data.resolver::resolveClass)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
	}

	@Override
	public String result() {
		if (MapUtility.IGNORED_NAME.contains(name)) {
			return name;
		}

		Collection<String> collection = targets.stream()
				.map(target -> data.resolver.resolveMember(target, name, desc, ResolveUtility.FLAG_UNIQUE | ResolveUtility.FLAG_RECURSIVE))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.map(data.mapper::mapName)
				.distinct().collect(Collectors.toList());

		if (collection.size() > 1) {
			data.logger.error(String.format(Message.CONFLICT_MAPPING, this.name, collection));
		} else if (collection.isEmpty()) {
			data.logger.warn(String.format(Message.NO_MAPPING_RECURSIVE, this.name, targets));
		}

		return collection.stream().findFirst().orElse(name);
	}
}
