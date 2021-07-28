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
