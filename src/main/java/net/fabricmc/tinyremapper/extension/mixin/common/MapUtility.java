package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrRemapper;

public final class MapUtility {
	private final TrRemapper remapper;
	private final Logger logger;

	public MapUtility(TrRemapper remapper, Logger logger) {
		this.remapper = Objects.requireNonNull(remapper);
		this.logger = Objects.requireNonNull(logger);
	}

	public String map(TrMember member) {
		if (member.isField()) {
			return remapper.mapFieldName(member.getOwner().getName(), member.getName(), member.getDesc());
		} else {
			return remapper.mapMethodName(member.getOwner().getName(), member.getName(), member.getDesc());
		}
	}

	public String mapDesc(TrMember member) {
		if (member.isField()) {
			return remapper.mapDesc(member.getDesc());
		} else {
			return remapper.mapMethodDesc(member.getDesc());
		}
	}
}
