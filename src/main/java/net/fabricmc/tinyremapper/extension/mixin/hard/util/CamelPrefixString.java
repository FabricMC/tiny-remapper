package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Objects;

import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;

public class CamelPrefixString implements IConvertibleString {
	private final String prefix;
	private final String text;
	private final String original;

	public CamelPrefixString(String prefix, String text) {
		original = Objects.requireNonNull(text);

		if (text.startsWith(prefix)) {
			this.prefix = Objects.requireNonNull(prefix);
			this.text = StringUtility.removeCamelPrefix(prefix, text);
		} else {
			this.prefix = "";
			this.text = text;
		}
	}

	@Override
	public String getOriginal() {
		return original;
	}

	@Override
	public String getConverted() {
		return text;
	}

	@Override
	public String getReverted(String newText) {
		return StringUtility.addCamelPrefix(prefix, newText);
	}
}
