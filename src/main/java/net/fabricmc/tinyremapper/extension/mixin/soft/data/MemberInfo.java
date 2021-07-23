package net.fabricmc.tinyremapper.extension.mixin.soft.data;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;

public final class MemberInfo {
	private final String owner;		// desc
	private final String name;		// name
	private final String quantifier;
	private final String desc;		// desc

	public MemberInfo(String owner, String name, String quantifier, String desc) {
		this.owner = Objects.requireNonNull(owner);
		this.name = Objects.requireNonNull(name);
		this.quantifier = Objects.requireNonNull(quantifier);
		this.desc = Objects.requireNonNull(desc);
	}

	public String getOwner() {
		return owner;
	}

	public String getName() {
		return name;
	}

	public String getQuantifier() {
		return quantifier;
	}

	public String getDesc() {
		return desc;
	}

	public MemberType getType() {
		if (desc.isEmpty()) {
			return null;
		}

		return desc.startsWith("(") ? MemberType.METHOD : MemberType.FIELD;
	}

	public static boolean isRegex(String str) {
		return str.endsWith("/");
	}

	public static boolean isDynamic(String str) {
		return str.startsWith("@");
	}

	public static MemberInfo parse(String str) {
		if (isRegex(str) || isDynamic(str)) {
			return null;
		}

		str = str.replaceAll("\\s", "");

		// str = owner | name | quantifier | descriptor

		int sep;
		String owner, name, quantifier, descriptor;
		owner = name = quantifier = descriptor = "";

		if ((sep = str.indexOf('(')) >= 0) {
			descriptor = str.substring(sep);
			str = str.substring(0, sep);
		} else if ((sep = str.indexOf(":")) >= 0) {
			descriptor = str.substring(sep + 1);
			str = str.substring(0, sep);
		}

		// str = owner | name | quantifier

		if ((sep = str.indexOf('*')) >= 0) {
			quantifier = str.substring(sep);
			str = str.substring(0, sep);
		} else if ((sep = str.indexOf('{')) >= 0) {
			quantifier = str.substring(sep);
			str = str.substring(0, sep);
		}

		// str = owner | name

		if ((sep = str.indexOf(';')) >= 0) {
			owner = str.substring(0, sep + 1);
			str = str.substring(sep + 1);
		} else if ((sep = str.lastIndexOf('.')) >= 0) {
			owner = StringUtility.classNameToDesc(str.substring(0, sep).replace('.', '/'));
			str = str.substring(sep + 1);
		}

		// str = owner or name
		if (str.contains("/") || str.contains(".")) {
			owner = str;
		} else {
			name = str;
		}

		return new MemberInfo(owner, name, quantifier, descriptor);
	}

	@Override
	public String toString() {
		String str = owner + name + quantifier;

		if (!desc.isEmpty()) {
			str += Objects.equals(getType(), MemberType.FIELD) ? ":" : "";
			str += desc;
		}

		return str;
	}
}
