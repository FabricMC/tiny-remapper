package net.fabricmc.tinyremapper.api;

import java.util.Objects;

public abstract class MemberHeader {
	public final ClassHeader header;
	public final int access;
	public final String name, desc, sign;

	protected MemberHeader(ClassHeader header, int access, String name, String desc, String sign) {
		this.header = header;
		this.access = access;
		this.name = name;
		this.desc = desc;
		this.sign = sign;
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
		if (!Objects.equals(this.header, header1.header)) {
			return false;
		}
		if (!Objects.equals(this.name, header1.name)) {
			return false;
		}
		if (!Objects.equals(this.desc, header1.desc)) {
			return false;
		}
		return Objects.equals(this.sign, header1.sign);
	}

	@Override
	public int hashCode() {
		int result = this.header != null ? this.header.hashCode() : 0;
		result = 31 * result + this.access;
		result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
		result = 31 * result + (this.desc != null ? this.desc.hashCode() : 0);
		result = 31 * result + (this.sign != null ? this.sign.hashCode() : 0);
		return result;
	}
}
