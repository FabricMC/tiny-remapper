package net.fabricmc.tinyremapper.api;

import java.util.List;
import java.util.Objects;

public class ClassHeader {
	public final int version, access;
	public final String internalName, superName, signature;
	public final List<String> interfaces;

	public ClassHeader(int version, int access, String internalName, String signature, String superName, List<String> interfaces) {
		this.version = version;
		this.access = access;
		this.internalName = internalName;
		this.superName = superName;
		this.signature = signature;
		this.interfaces = interfaces;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ClassHeader)) {
			return false;
		}

		ClassHeader header = (ClassHeader) o;

		if (this.version != header.version) {
			return false;
		}
		if (this.access != header.access) {
			return false;
		}
		if (!Objects.equals(this.internalName, header.internalName)) {
			return false;
		}
		if (!Objects.equals(this.superName, header.superName)) {
			return false;
		}
		return Objects.equals(this.interfaces, header.interfaces);
	}

	@Override
	public int hashCode() {
		int result = this.version;
		result = 31 * result + this.access;
		result = 31 * result + (this.internalName != null ? this.internalName.hashCode() : 0);
		result = 31 * result + (this.superName != null ? this.superName.hashCode() : 0);
		result = 31 * result + (this.interfaces != null ? this.interfaces.hashCode() : 0);
		return result;
	}
}
