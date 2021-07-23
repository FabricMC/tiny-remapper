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

public class NamedMappable implements IMappable<String> {
	private final CommonData data;
	private final String name;
	private final String desc;
	private final Collection<TrClass> targets;

	public NamedMappable(CommonData data, String name, String desc, Collection<TrClass> targets) {
		this.data = Objects.requireNonNull(data);
		this.name = Objects.requireNonNull(name);
		this.desc = Objects.requireNonNull(desc);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public String result() {
		final ResolveUtility resolver = new ResolveUtility(data.logger);
		final MapUtility mapper = new MapUtility(data.remapper, data.logger);

		Collection<String> collection = targets.stream()
				.map(target -> resolver.resolve(target, name, desc, ResolveUtility.FLAG_UNIQUE | ResolveUtility.FLAG_RECURSIVE))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.map(mapper::map)
				.distinct().collect(Collectors.toList());

		if (collection.size() > 1) {
			data.logger.error("Conflict mapping detected, " + name + " -> " + collection);
		} else if (collection.isEmpty()) {
			data.logger.error("Cannot remap " + name + " because it does not exists in any of the targets " + targets);
		}

		return collection.stream().findFirst().orElse(name);
	}
}