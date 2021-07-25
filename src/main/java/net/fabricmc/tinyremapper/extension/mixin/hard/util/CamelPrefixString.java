package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Objects;

import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;

public class CamelPrefixString implements IConvertibleString {
	private final String prefix;
	private final String text;

	public CamelPrefixString(String prefix, String text) {
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
		return StringUtility.addCamelPrefix(prefix, text);
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
