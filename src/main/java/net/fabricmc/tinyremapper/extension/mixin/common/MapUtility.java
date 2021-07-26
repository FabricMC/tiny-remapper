package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrRemapper;

public final class MapUtility {
	private final TrRemapper remapper;
	private final Logger logger;

	public static final List<String> IGNORED_NAME = Arrays.asList("<init>", "<clinit>");

	public MapUtility(TrRemapper remapper, Logger logger) {
		this.remapper = Objects.requireNonNull(remapper);
		this.logger = Objects.requireNonNull(logger);
	}

	public String mapName(TrClass _class) {
		return remapper.map(_class.getName());
	}

	public String mapName(TrMember member) {
		if (member.isField()) {
			return remapper.mapFieldName(member.getOwner().getName(), member.getName(), member.getDesc());
		} else {
			return remapper.mapMethodName(member.getOwner().getName(), member.getName(), member.getDesc());
		}
	}

	public String mapDesc(TrClass _class) {
		return StringUtility.classNameToDesc(mapName(_class));
	}

	public String mapDesc(TrMember member) {
		if (member.isField()) {
			return remapper.mapDesc(member.getDesc());
		} else {
			return remapper.mapMethodDesc(member.getDesc());
		}
	}

	public String mapOwner(TrMember member) {
		return mapName(member.getOwner());
	}

	public TrRemapper asTrRemapper() {
		return this.remapper;
	}
}
