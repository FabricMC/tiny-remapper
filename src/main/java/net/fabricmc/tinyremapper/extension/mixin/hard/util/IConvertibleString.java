package net.fabricmc.tinyremapper.extension.mixin.hard.util;

public interface IConvertibleString {
	String getOriginal();
	String getConverted();
	String getReverted(String newText);
}
