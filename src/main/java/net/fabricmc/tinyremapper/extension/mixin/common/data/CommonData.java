package net.fabricmc.tinyremapper.extension.mixin.common.data;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility;

public final class CommonData {
	private final TrEnvironment environment;
	public final Logger logger;

	public final ResolveUtility resolver;
	public final MapUtility mapper;

	public CommonData(TrEnvironment environment, Logger logger) {
		this.environment = Objects.requireNonNull(environment);
		this.logger = Objects.requireNonNull(logger);

		this.resolver = new ResolveUtility(environment, logger);
		this.mapper = new MapUtility(environment.getRemapper(), logger);
	}

	public void propagate(TrMember member, String newName) {
		this.environment.propagate(member, newName);
	}
}
