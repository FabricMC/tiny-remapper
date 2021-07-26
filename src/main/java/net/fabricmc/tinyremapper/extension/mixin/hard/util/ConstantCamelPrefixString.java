package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Objects;

import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;

public class ConstantCamelPrefixString implements IConvertibleString {
	private final String prefix;
	private final String text;

	public ConstantCamelPrefixString(String prefix, String text) {
		this.prefix = Objects.requireNonNull(prefix);
		this.text = StringUtility.removePrefix(prefix, text);
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
