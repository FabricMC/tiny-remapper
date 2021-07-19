package net.fabricmc.tinyremapper.extension.mixin.data;

import java.util.Objects;
import java.util.regex.Pattern;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;

public final class MemberInfo {
	public final AnnotationType type;
	public final String owner;		// desc
	public final String name;		// name
	public final String quantifier;
	public final String desc;		// desc

	private static final Pattern REGEX_PATTERN = Pattern.compile("^(/|name=/|desc=/|owner=/).*/$");
	private static final Pattern CLASS_PATTERN = Pattern.compile("^([A-Za-z0-9$_]+/)+[A-Za-z0-9$_]+$");

	public MemberInfo(AnnotationType type, String owner,
					String name, String quantifier, String desc) {
		this.type = type;
		this.owner = Objects.requireNonNull(owner);
		this.name = Objects.requireNonNull(name);
		this.quantifier = Objects.requireNonNull(quantifier);
		this.desc = Objects.requireNonNull(desc);
	}

	public static boolean isRegex(String str) {
		return REGEX_PATTERN.matcher(str).matches();
	}

	public static boolean isClass(String str) {
		return CLASS_PATTERN.matcher(str).matches();
	}

	public static MemberInfo parse(String str) {
		if (isRegex(str)) {
			throw new RuntimeException("Cannot parse a regex target selector");
		} else if (isClass(str)) {
			return new MemberInfo(AnnotationType.CLASS, "", str, "", "");
		} else {
			// Member: owner [sep1] name [sep2] quantifier [sep3] descriptor

			AnnotationType type = null;
			String owner = "";
			String name;
			String quantifier = "";
			String descriptor = "";

			int sep;

			if ((sep = str.indexOf('(')) >= 0) {	// sep3
				type = AnnotationType.METHOD;
				descriptor = str.substring(sep);
				str = str.substring(0, sep);
			} else if ((sep = str.indexOf(':')) >= 0) {
				type = AnnotationType.FIELD;
				descriptor = str.substring(sep + 1);
				str = str.substring(0, sep);
			}

			if ((sep = str.indexOf(';')) >= 0) {	// sep1
				owner = str.substring(0, sep + 1);
				str = str.substring(sep + 1);
			} else if ((sep = str.lastIndexOf('.')) >= 0) {
				owner = CommonUtility.classNameToDesc(str.substring(0, sep).replace('.', '/'));
				str = str.substring(sep + 1);
			}

			if ((sep = str.indexOf('*')) >= 0) {	// sep2
				quantifier = str.substring(sep);
				str = str.substring(0, sep);
			} else if ((sep = str.indexOf('{')) >= 0) {
				quantifier = str.substring(sep);
				str = str.substring(0, sep);
			}

			name = str;

			return new MemberInfo(type, owner, name, quantifier, descriptor);
		}
	}

	@Override
	public String toString() {
		if (this.type != null && this.type.equals(AnnotationType.CLASS)) {
			return this.name;
		} else {
			String str = "";

			if (!this.owner.isEmpty()) {
				str += this.owner;
			}

			if (!this.name.isEmpty()) {
				str += this.name;
			}

			if (!this.quantifier.isEmpty()) {
				str += quantifier;
			}

			if (!this.desc.isEmpty()) {
				assert this.type != null;

				if (this.type.equals(AnnotationType.FIELD)) {
					str += (":" + this.desc);
				} else {
					str += this.desc;
				}
			}

			return str;
		}
	}
}
