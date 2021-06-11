package net.fabricmc.tinyremapper.impl;

import java.util.List;
import java.util.Objects;

import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.ClassHeader;
import net.fabricmc.tinyremapper.api.Classpath;

public class ClassHeaderImpl implements ClassHeader {
	public final TinyRemapper remapper;
	public final int version, access;
	public final String internalName, superName, signature;
	public final List<String> interfaces;

	public ClassHeaderImpl(TinyRemapper remapper, int version, int access, String internalName, String signature, String superName, List<String> interfaces) {
		this.remapper = remapper;
		this.version = version;
		this.access = access;
		this.internalName = internalName;
		this.superName = superName;
		this.signature = signature;
		this.interfaces = interfaces;
	}

	@Override
	public Classpath getClasspath() {
		return this.remapper;
	}

	@Override
	public int getAccess() {
		return this.access;
	}

	@Override
	public String getName() {
		return this.internalName;
	}

	@Override
	public String getSuperName() {
		return this.superName;
	}

	@Override
	public String getSignature() {
		return this.signature;
	}

	@Override
	public List<String> getInterfaceList() {
		return this.interfaces;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ClassHeaderImpl)) {
			return false;
		}

		ClassHeaderImpl header = (ClassHeaderImpl) o;

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
