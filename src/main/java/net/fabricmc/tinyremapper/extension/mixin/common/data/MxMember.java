package net.fabricmc.tinyremapper.extension.mixin.common.data;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;

public class MxMember {
	private final String owner;
	private final String name;
	private final String desc;

	MxMember(String owner, String name, String desc) {
		this.owner = Objects.requireNonNull(owner);
		this.name = Objects.requireNonNull(name);
		this.desc = Objects.requireNonNull(desc);
	}

	public String getName() {
		return name;
	}

	public String getDesc() {
		return desc;
	}

	public MemberType getType() {
		return StringUtility.isFieldDesc(desc) ? MemberType.FIELD : MemberType.METHOD;
	}

	public MxClass getOwner() {
		return new MxClass(owner);
	}

	public TrMember asTrMember(TrEnvironment environment) {
		if (getType().equals(MemberType.FIELD)) {
			return getOwner().asTrClass(environment).getField(name, desc);
		} else {
			return getOwner().asTrClass(environment).getMethod(name, desc);
		}
	}
}
