package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.Locale;

public final class StringUtility {
	public static String removeCamelPrefix(String prefix, String str) {
		if (str.startsWith(prefix)) {
			str = str.substring(prefix.length());

			return str.isEmpty() ? str
					: str.substring(0, 1).toLowerCase(Locale.ROOT) + str.substring(1);
		}

		throw new RuntimeException(prefix + " is not the prefix of " + str);
	}

	public static String addCamelPrefix(String prefix, String str) {
		return str.isEmpty() ? prefix
				: prefix + str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
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
		return isClassDesc(text);
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
