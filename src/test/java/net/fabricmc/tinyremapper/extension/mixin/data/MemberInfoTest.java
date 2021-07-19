package net.fabricmc.tinyremapper.extension.mixin.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MemberInfoTest {
	@Test
	void isRegex() {
		assertTrue(MemberInfo.isRegex("/^foo/"));
		assertTrue(MemberInfo.isRegex("/bar$/ desc=/^\\(I\\)/"));
		assertTrue(MemberInfo.isRegex("name=/bar$/ desc=/^\\(I\\)/"));
		assertTrue(MemberInfo.isRegex("/Entity/"));
		assertTrue(MemberInfo.isRegex("owner=/\\/google\\//"));
		assertFalse(MemberInfo.isRegex("func_1234_a"));
		assertFalse(MemberInfo.isRegex("field_5678_z:Ljava/lang/String;"));
		assertFalse(MemberInfo.isRegex("Lfoo/bar/Baz;func_1234_a(DDD)V"));
	}

	@Test
	void parse() {
		MemberInfo info;

		info = MemberInfo.parse("{2}(Z)V");
		assertEquals(info.type, AnnotationType.METHOD);
		assertEquals(info.owner, "");
		assertEquals(info.name, "");
		assertEquals(info.quantifier, "{2}");
		assertEquals(info.desc, "(Z)V");
		assertEquals(info.toString(), "{2}(Z)V");

		info = MemberInfo.parse("field_5678_z:Ljava/lang/String;");
		assertEquals(info.type, AnnotationType.FIELD);
		assertEquals(info.owner, "");
		assertEquals(info.name, "field_5678_z");
		assertEquals(info.quantifier, "");
		assertEquals(info.desc, "Ljava/lang/String;");
		assertEquals(info.toString(), "field_5678_z:Ljava/lang/String;");

		info = MemberInfo.parse("Lfoo/bar/Baz;func_1234_a(DDD)V");
		assertEquals(info.type, AnnotationType.METHOD);
		assertEquals(info.owner, "Lfoo/bar/Baz;");
		assertEquals(info.name, "func_1234_a");
		assertEquals(info.quantifier, "");
		assertEquals(info.desc, "(DDD)V");
		assertEquals(info.toString(), "Lfoo/bar/Baz;func_1234_a(DDD)V");

		info = MemberInfo.parse("foo.bar.Baz.func_1234_a(DDD)V");
		assertEquals(info.type, AnnotationType.METHOD);
		assertEquals(info.owner, "Lfoo/bar/Baz;");
		assertEquals(info.name, "func_1234_a");
		assertEquals(info.quantifier, "");
		assertEquals(info.desc, "(DDD)V");
		assertEquals(info.toString(), "Lfoo/bar/Baz;func_1234_a(DDD)V");
	}

	@Test
	void isClass() {
		assertTrue(MemberInfo.isClass("net/minecraft/world/entity/projectile/FireworkRocketEntity"));
		assertTrue(MemberInfo.isClass("net/mine_craft/wo$$rld/entity/projectile/FireworkRocketEntity"));
		assertFalse(MemberInfo.isClass("/^foo/"));
		assertFalse(MemberInfo.isClass("func_1234_a"));
		assertFalse(MemberInfo.isClass("net/minecraft/world/entity/projectile/"));
		assertFalse(MemberInfo.isClass("net/minecraft/world/entity//projectile/FireworkRocketEntity"));
	}
}
