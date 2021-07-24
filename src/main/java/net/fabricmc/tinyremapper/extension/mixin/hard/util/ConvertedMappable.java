package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;

public abstract class ConvertedMappable extends HardTargetMappable {
	private final Collection<TrClass> targets;

	public ConvertedMappable(CommonData data, MxMember self, Collection<String> targets) {
		super(data, self);

		this.targets = Objects.requireNonNull(targets).stream().map(data.environment::getClass).filter(Objects::nonNull).collect(Collectors.toList());
	}

	protected abstract String getConvertedName();
	protected abstract String getConvertedDesc();
	protected abstract String revertName(String name);

	protected Stream<String> mapMultiTarget(String name, String desc) {
		final ResolveUtility resolver = new ResolveUtility(data.logger);
		final MapUtility mapper = new MapUtility(data.remapper, data.logger);

		return targets.stream()
				.map(target -> resolver.resolve(target, name, desc, ResolveUtility.FLAG_UNIQUE | ResolveUtility.FLAG_RECURSIVE))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.map(mapper::map);
	}

	@Override
	protected Optional<String> getMappedName() {
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
