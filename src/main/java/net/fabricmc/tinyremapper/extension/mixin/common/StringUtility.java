package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StringUtility {
	public static String addPrefix(String prefix, String text) {
		return prefix + text;
	}

	public static String removePrefix(String prefix, String text) {
		if (text.startsWith(prefix)) {
			return text.substring(prefix.length());
		} else {
			throw new RuntimeException(String.format("%s does not start with %s", text, prefix));
		}
	}

	public static String addCamelPrefix(String prefix, String text) {
		if (text.isEmpty()) {
			return prefix;
		} else {
			return prefix + text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
		}
	}

	public static String removeCamelPrefix(String prefix, String text) {
		text = removePrefix(prefix, text);

		if (text.isEmpty()) {
			return text;
		} else {
			return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
		}
	}

	private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("([A-Za-z0-9_$]+\\/)+[A-Za-z0-9_$]+");
	private static final Pattern CLASS_DESC_PATTERN = Pattern.compile("L([A-Za-z0-9_$]+\\/)+[A-Za-z0-9_$]+;");

	public static boolean isClassName(String text) {
		return CLASS_NAME_PATTERN.matcher(text).matches();
	}

	public static boolean isClassDesc(String text) {
		return CLASS_DESC_PATTERN.matcher(text).matches();
	}

	public static boolean isFieldDesc(String text) {
		return !isMethodDesc(text);
	}

	public static boolean isMethodDesc(String text) {
		return text.startsWith("(");
	}

	public static String classNameToDesc(String className) {
		return "L" + className + ";";
	}

	public static String classDescToName(String classDesc) {
		return classDesc.substring(1, classDesc.length() - 1);
	}
}
