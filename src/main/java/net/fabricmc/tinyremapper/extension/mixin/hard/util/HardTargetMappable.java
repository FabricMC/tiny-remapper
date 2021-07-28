package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Objects;
import java.util.Optional;

import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;

public abstract class HardTargetMappable implements IMappable<Void> {
	protected CommonData data;
	protected MxMember self;

	public HardTargetMappable(CommonData data, MxMember self) {
		this.data = Objects.requireNonNull(data);
		this.self = Objects.requireNonNull(self);
	}

	protected abstract Optional<String> getMappedName();

	@Override
	public Void result() {
		getMappedName().ifPresent(x -> data.propagate(self.asTrMember(data.resolver), x));
		return null;
	}
}
