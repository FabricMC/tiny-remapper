package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IConvertibleStringTest {
	@Test
	void IdentityString() {
		IConvertibleString str;

		str = new IdentityString("bla");
		assertEquals("bla", str.getOriginal());
		assertEquals("bla", str.getConverted());
		assertEquals("abc", str.getReverted("abc"));
	}

	@Test
	void PrefixString() {
		IConvertibleString str;

		str = new PrefixString("abc", "abcde");
		assertEquals("abcde", str.getOriginal());
		assertEquals("de", str.getConverted());
		assertEquals("abcbla", str.getReverted("bla"));

		str = new PrefixString("abc", "blade");
		assertEquals("blade", str.getOriginal());
		assertEquals("blade", str.getConverted());
		assertEquals("aaa", str.getReverted("aaa"));
	}

	@Test
	void CamelPrefixString() {
		IConvertibleString str;

		str = new CamelPrefixString("abc", "abcde");
		assertEquals("abcde", str.getOriginal());
		assertEquals("de", str.getConverted());
		assertEquals("abcAbc", str.getReverted("abc"));

		str = new CamelPrefixString("abc", "abcDe");
		assertEquals("abcDe", str.getOriginal());
		assertEquals("de", str.getConverted());
		assertEquals("abcAbc", str.getReverted("abc"));
	}
}