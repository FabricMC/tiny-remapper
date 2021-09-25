/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, FabricMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.Locale;
import java.util.regex.Pattern;

import net.fabricmc.tinyremapper.api.TrMember.MemberType;

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

		if (text.isEmpty() || text.toUpperCase(Locale.ROOT).equals(text)) {
			return text;
		} else {
			if (Character.isLowerCase(text.charAt(0))) throw new RuntimeException(String.format("%s does not start with camel prefix %s", text, prefix));
			return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
		}
	}

	private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("(([A-Za-z0-9_$]+/)+[A-Za-z0-9_$]+)");
	private static final Pattern CLASS_DESC_PATTERN = Pattern.compile("(L" + CLASS_NAME_PATTERN + ";)");

	public static boolean isClassName(String text) {
		return CLASS_NAME_PATTERN.matcher(text).matches();
	}

	public static boolean isClassDesc(String text) {
		return CLASS_DESC_PATTERN.matcher(text).matches();
	}

	private static final Pattern FIELD_DESC_PATTERN = Pattern.compile("(\\[*(" + CLASS_DESC_PATTERN +"|[BCDFIJSZ]))");
	private static final Pattern METHOD_DESC_PATTERN = Pattern.compile("(\\(" + FIELD_DESC_PATTERN + "*\\)(" + FIELD_DESC_PATTERN + "|V))");

	public static boolean isFieldDesc(String text) {
		return FIELD_DESC_PATTERN.matcher(text).matches();
	}

	public static boolean isMethodDesc(String text) {
		return METHOD_DESC_PATTERN.matcher(text).matches();
	}

	public static MemberType getTypeByDesc(String text) {
		if (StringUtility.isFieldDesc(text)) {
			return MemberType.FIELD;
		} else if (StringUtility.isMethodDesc(text)) {
			return MemberType.METHOD;
		} else {
			throw new RuntimeException(String.format("%s is neither field descriptor nor method descriptor.", text));
		}
	}

	private static final Pattern INTERNAL_CLASS_PATTERN = Pattern.compile("java/.*");

	public static boolean isInternalClassName(String className) {
		if (!isClassName(className)) throw new RuntimeException(String.format("%s is not a class name.", className));

		if (INTERNAL_CLASS_PATTERN.matcher(className).matches()) {
			return true;
		}

		try {
			Class.forName(className.replace('/', '.'));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static String classNameToDesc(String className) {
		if (!isClassName(className)) throw new RuntimeException(String.format("%s is not a class name.", className));
		return "L" + className + ";";
	}

	public static String classDescToName(String classDesc) {
		if (!isClassDesc(classDesc)) throw new RuntimeException(String.format("%s is not a class descriptor.", classDesc));
		return classDesc.substring(1, classDesc.length() - 1);
	}
}
