package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Objects;

public class IdentityString implements IConvertibleString {
	private final String text;

	public IdentityString(String text) {
		this.text = Objects.requireNonNull(text);
	}

	@Override
	public String getOriginal() {
		return text;
	}

	@Override
	public String getConverted() {
		return text;
	}

	@Override
	public String getReverted(String newText) {
		return newText;
	}
}
