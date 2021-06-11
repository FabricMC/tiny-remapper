package net.fabricmc.tinyremapper.api;

import java.util.Objects;

public class FieldHeader extends MemberHeader {
	/**
	 * @see org.objectweb.asm.ClassVisitor#visitField(int, String, String, String, Object)
	 */
	public final Object value;
	public FieldHeader(ClassHeader header, int access, String name, String desc, String sign, Object value) {
		super(header, access, name, desc, sign);
		this.value = value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof FieldHeader)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}

		FieldHeader header = (FieldHeader) o;

		return Objects.equals(this.value, header.value);
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (this.value != null ? this.value.hashCode() : 0);
		return result;
	}
}
