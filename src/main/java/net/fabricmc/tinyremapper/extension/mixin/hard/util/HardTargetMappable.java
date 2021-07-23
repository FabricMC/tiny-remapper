package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Objects;
import java.util.Optional;

import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;

public abstract class HardTargetMappable implements IMappable<Void> {
	protected CommonData data;
	protected TrMember self;

	public HardTargetMappable(CommonData data, MxMember self) {
		this.data = Objects.requireNonNull(data);
		this.self = Objects.requireNonNull(self).asTrMember(data.environment);
	}

	protected abstract Optional<String> getNewName();

	@Override
	public Void result() {
		getNewName().ifPresent(x -> data.environment.propagate(self, x));
		return null;
	}
}
