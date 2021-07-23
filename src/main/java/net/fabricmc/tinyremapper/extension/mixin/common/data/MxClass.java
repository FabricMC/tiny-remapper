package net.fabricmc.tinyremapper.extension.mixin.common.data;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;

public class MxClass {
	private final String name;

	public MxClass(String name) {
		this.name = Objects.requireNonNull(name);
	}

	public String getName() {
		return name;
	}

	public MxMember getField(String name, String desc) {
		return new MxMember(this.name, name, desc);
	}

	public MxMember getMethod(String name, String desc) {
		return new MxMember(this.name, name, desc);
	}

	public TrClass asTrClass(TrEnvironment environment) {
		return environment.getClass(name);
	}
}
