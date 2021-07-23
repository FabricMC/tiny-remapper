package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.Resolver;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;

public abstract class ConvertedMappable extends HardTargetMappable {
	private final Collection<TrClass> targets;

	public ConvertedMappable(CommonData data, TrMember self, Collection<TrClass> targets) {
		super(data, self);

		this.targets = Objects.requireNonNull(targets);
	}

	protected abstract String getConvertedName();
	protected abstract String getConvertedDesc();
	protected abstract String revertName(String name);

	protected Stream<String> mapMultiTarget(String name, String desc) {
		final Resolver resolver = new Resolver(data.environment, data.logger);
		final MapUtility mapper = new MapUtility(data.remapper, data.logger);

		return targets.stream()
				.map(target -> resolver.resolve(target, name, desc, Resolver.FLAG_UNIQUE))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.map(mapper::map);
	}

	@Override
	protected Optional<String> getNewName() {
		List<String> collection = mapMultiTarget(getConvertedName(), getConvertedDesc())
				.map(this::revertName)
				.distinct().collect(Collectors.toList());

		if (collection.size() > 1) {
			data.logger.error("Conflict mapping detected, " + self.getName() + " -> " + collection);
		} else if (collection.isEmpty()) {
			data.logger.error("Cannot remap " + self.getName() + " because it does not exists in any of the targets " + targets);
		}

		return collection.stream().findFirst();
	}
}
