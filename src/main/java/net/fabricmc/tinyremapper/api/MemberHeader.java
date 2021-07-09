package net.fabricmc.tinyremapper.api;

import java.util.Objects;

public class MemberHeader {
	public final ClassHeader owner;
	public final int access;
	public final String name, desc;

	public MemberHeader(ClassHeader owner, int access, String name, String desc) {
		this.owner = owner;
		this.access = access;
		this.name = name;
		this.desc = desc;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof MemberHeader)) {
			return false;
		}

		MemberHeader header1 = (MemberHeader) o;

		if (this.access != header1.access) {
			return false;
		}

		return Objects.equals(this.owner, header1.owner) && Objects.equals(this.name, header1.name) && Objects.equals(this.desc, header1.desc);
	}

	@Override
	public int hashCode() {
		int result = this.owner != null ? this.owner.hashCode() : 0;
		result = 31 * result + this.access;
		result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
		result = 31 * result + (this.desc != null ? this.desc.hashCode() : 0);
		return result;
	}
}
