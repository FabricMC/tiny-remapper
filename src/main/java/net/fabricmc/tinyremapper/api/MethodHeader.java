package net.fabricmc.tinyremapper.api;

import java.util.List;
import java.util.Objects;

public class MethodHeader extends MemberHeader{
	public final List<String> exceptions;

	public MethodHeader(ClassHeader header, int access, String name, String desc, String sign, List<String> exceptions) {
		super(header, access, name, desc, sign);
		this.exceptions = exceptions;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof MethodHeader)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}

		MethodHeader header = (MethodHeader) o;
		return Objects.equals(this.exceptions, header.exceptions);
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (this.exceptions != null ? this.exceptions.hashCode() : 0);
		return result;
	}
}
