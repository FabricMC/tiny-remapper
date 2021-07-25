package net.fabricmc.tinyremapper.extension.mixin.common.data;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrRemapper;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;

public final class CommonData {
	public final TrRemapper remapper;
	public final TrEnvironment environment;
	public final Logger logger;

	public final ResolveUtility resolver;

	public CommonData(TrEnvironment environment, Logger logger) {
		this.remapper = environment.getRemapper();
		this.environment = Objects.requireNonNull(environment);
		this.logger = Objects.requireNonNull(logger);

		this.resolver = new ResolveUtility(environment, logger);
	}
}
