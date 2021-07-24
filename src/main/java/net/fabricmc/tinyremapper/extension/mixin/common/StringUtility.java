package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.Locale;

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



	/**
	 * Some naive checking.
	 */
	public static boolean isClassDesc(String text) {
		return text.startsWith("L") && text.endsWith(";");
	}

	public static boolean isClassName(String text) {
		return !isClassDesc(text);
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
