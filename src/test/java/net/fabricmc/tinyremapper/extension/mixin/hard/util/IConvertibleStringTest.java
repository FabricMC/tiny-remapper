package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

		assertThrows(RuntimeException.class, () -> new PrefixString("abc", "blade"));
	}

	@Test
	void CamelPrefixString() {
		IConvertibleString str;

		str = new CamelPrefixString("abc", "abcDe");
		assertEquals("abcDe", str.getOriginal());
		assertEquals("de", str.getConverted());
		assertEquals("abcAbc", str.getReverted("abc"));

		assertThrows(RuntimeException.class, () -> new CamelPrefixString("abc", "abcde"));
		assertThrows(RuntimeException.class, () -> new CamelPrefixString("abc", "def"));
		assertDoesNotThrow(() -> new CamelPrefixString("abc", "abc123"));

		str = new CamelPrefixString("get", "getBIOME");
		assertEquals("getBIOME", str.getOriginal());
		assertEquals("BIOME", str.getConverted());
		assertEquals("getField_1234", str.getReverted("field_1234"));
	}
}
