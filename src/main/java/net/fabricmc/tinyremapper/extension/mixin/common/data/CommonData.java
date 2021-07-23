package net.fabricmc.tinyremapper.extension.mixin.common.data;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrRemapper;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;

public final class CommonData {
	public final TrRemapper remapper;
	public final TrEnvironment environment;
	public final Logger logger;

	public CommonData(TrEnvironment environment, Logger logger) {
		this.remapper = environment.getRemapper();
		this.environment = Objects.requireNonNull(environment);
		this.logger = Objects.requireNonNull(logger);
	}
}
