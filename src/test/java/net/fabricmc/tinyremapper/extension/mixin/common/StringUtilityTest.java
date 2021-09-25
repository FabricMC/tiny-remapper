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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.fabricmc.tinyremapper.api.TrMember.MemberType;

class StringUtilityTest {
	@Test
	void addPrefix() {
		assertEquals("prefix123", StringUtility.addPrefix("prefix", "123"));
		assertEquals("BlaBlaAaa", StringUtility.addPrefix("Bla", "BlaAaa"));
	}

	@Test
	void removePrefix() {
		assertEquals("123", StringUtility.removePrefix("prefix", "prefix123"));
		assertThrows(RuntimeException.class, () -> StringUtility.removePrefix("prefix", "noprefix123"));
	}

	@Test
	void addCamelPrefix() {
		assertEquals("prefix", StringUtility.addCamelPrefix("prefix", ""));
		assertEquals("prefix123", StringUtility.addCamelPrefix("prefix", "123"));
		assertEquals("prefixAbc", StringUtility.addCamelPrefix("prefix", "abc"));
		assertEquals("prefixAbc", StringUtility.addCamelPrefix("prefix", "Abc"));
	}

	@Test
	void removeCamelPrefix() {
		assertEquals("123", StringUtility.removeCamelPrefix("prefix", "prefix123"));
		assertEquals("abc", StringUtility.removeCamelPrefix("prefix", "prefixAbc"));
		assertEquals("", StringUtility.removeCamelPrefix("prefix", "prefix"));
		assertEquals("ABC", StringUtility.removeCamelPrefix("prefix", "prefixABC"));
		assertEquals("ABC_DEF", StringUtility.removeCamelPrefix("prefix", "prefixABC_DEF"));
	}

	@Test
	void isClassName() {
		assertTrue(StringUtility.isClassName("com/github/logicf/class"));
		assertTrue(StringUtility.isClassName("com/github/logicf/$$__cl_a_ss"));
		assertTrue(StringUtility.isClassName("com/gith$$ub/log$$icf/cla$$ss"));
		assertTrue(StringUtility.isClassName("$$/__"));
		assertFalse(StringUtility.isClassName("abc"));
		assertFalse(StringUtility.isClassName("com/github/logicf/"));
		assertFalse(StringUtility.isClassName("com//github/logicf"));
		assertFalse(StringUtility.isClassName("com.github.logicf.class"));
	}

	@Test
	void isClassDesc() {
		assertTrue(StringUtility.isClassDesc("Lcom/github/logicf/class;"));
		assertTrue(StringUtility.isClassDesc("Lcom/github/logicf/$$__cl_a_ss;"));
		assertTrue(StringUtility.isClassDesc("Lcom/gith$$ub/log$$icf/cla$$ss;"));
		assertTrue(StringUtility.isClassDesc("L$$/__;"));
		assertFalse(StringUtility.isClassDesc("Labc;"));
		assertFalse(StringUtility.isClassDesc("Lcom/github/logicf/;"));
		assertFalse(StringUtility.isClassDesc("Lcom//github/logicf;"));
		assertFalse(StringUtility.isClassDesc("Lcom.github.logicf.class;"));
		assertFalse(StringUtility.isClassDesc("com/github/logicf/class"));
	}

	@Test
	void isFieldDesc() {
		assertTrue(StringUtility.isFieldDesc("I"));
		assertTrue(StringUtility.isFieldDesc("Ljava/lang/Object;"));
		assertTrue(StringUtility.isFieldDesc("[[[D"));
		assertTrue(StringUtility.isFieldDesc("[B"));
		assertTrue(StringUtility.isFieldDesc("[Ljava/lang/Object;"));
		assertTrue(StringUtility.isFieldDesc("[[Ljava/lang/Object;"));
		assertFalse(StringUtility.isFieldDesc("[[Ljava/lang/Object;["));
		assertFalse(StringUtility.isFieldDesc("[[Ljava/lang/Object;B"));
		assertFalse(StringUtility.isFieldDesc("com/github/logicf/class"));
		assertFalse(StringUtility.isFieldDesc("()V"));
		assertFalse(StringUtility.isFieldDesc("([Ljava/lang/Object;)D"));
	}

	@Test
	void isMethodDesc() {
		assertTrue(StringUtility.isMethodDesc("()V"));
		assertTrue(StringUtility.isMethodDesc("()Ljava/lang/Object;"));
		assertTrue(StringUtility.isMethodDesc("()[[[D"));
		assertTrue(StringUtility.isMethodDesc("([BBBDDBB[BBDLjava/lang/Object;)V"));
		assertTrue(StringUtility.isMethodDesc("([Ljava/lang/Object;)D"));
		assertTrue(StringUtility.isMethodDesc("([[Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
		assertFalse(StringUtility.isMethodDesc("com/github/logicf/class"));
		assertFalse(StringUtility.isMethodDesc("Ljava/lang/Object;"));
		assertFalse(StringUtility.isMethodDesc("[[[D"));
	}

	@Test
	void classNameToDesc() {
		assertEquals("Lcom/github/logicf/class;", StringUtility.classNameToDesc("com/github/logicf/class"));
		assertThrows(RuntimeException.class, () -> StringUtility.classNameToDesc("Lcom/github/logicf/class;"));
	}

	@Test
	void classDescToName() {
		assertEquals("com/github/logicf/class", StringUtility.classDescToName("Lcom/github/logicf/class;"));
		assertThrows(RuntimeException.class, () -> StringUtility.classDescToName("com/github/logicf/class"));
	}

	@Test
	void getTypeByDesc() {
		assertEquals(MemberType.FIELD, StringUtility.getTypeByDesc("[[[D"));
		assertEquals(MemberType.METHOD, StringUtility.getTypeByDesc("([BBBDDBB[BBDLjava/lang/Object;)V"));
		assertThrows(RuntimeException.class, () -> StringUtility.getTypeByDesc("bla"));
	}

	@Test
	void isInternalClassName() {
		assertTrue(StringUtility.isInternalClassName("java/lang/Boolean"));
		assertTrue(StringUtility.isInternalClassName("javax/crypto/Cipher"));
		assertFalse(StringUtility.isInternalClassName("net/minecraft/bla"));
		assertThrows(RuntimeException.class, () -> StringUtility.isInternalClassName("bla"));
	}
}
