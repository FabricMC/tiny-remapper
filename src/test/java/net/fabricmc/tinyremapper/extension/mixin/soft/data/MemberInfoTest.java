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

package net.fabricmc.tinyremapper.extension.mixin.soft.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.fabricmc.tinyremapper.api.TrMember.MemberType;

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

	@ParameterizedTest(name = "{0}")
	@MethodSource("parseTests")
	void parse(String input, Expected[] expectedParts) {
		MemberInfo info = MemberInfo.parse(input);

		for (Expected expected : expectedParts) {
			assertNotNull(info, "Fewer components than expected");
			expected.check(info);
			info = info.getNext();
		}

		assertNull(info, "More components than expected");
	}

	static Stream<Arguments> parseTests() {
		return Stream.of(
				testCase(
						"{2}(Z)V",
						new Expected(
								MemberType.METHOD,
								"",
								"",
								"{2}",
								"(Z)V",
								"{2}(Z)V"
						)
				),
				testCase(
						"field_5678_z:Ljava/lang/String;",
						new Expected(
								MemberType.FIELD,
								"",
								"field_5678_z",
								"",
								"Ljava/lang/String;",
								"field_5678_z:Ljava/lang/String;"
						)
				),
				testCase(
						"Lfoo/bar/Baz;func_1234_a(DDD)V",
						new Expected(
								MemberType.METHOD,
								"foo/bar/Baz",
								"func_1234_a",
								"",
								"(DDD)V",
								"Lfoo/bar/Baz;func_1234_a(DDD)V"
						)
				),
				testCase(
						"foo.bar.Baz.func_1234_a(DDD)V",
						new Expected(
								MemberType.METHOD,
								"foo/bar/Baz",
								"func_1234_a",
								"",
								"(DDD)V",
								"Lfoo/bar/Baz;func_1234_a(DDD)V"
						)
				),
				testCase(
						"java/lang/String",
						new Expected(
								null,
								"java/lang/String",
								"",
								"",
								"",
								"Ljava/lang/String;"
						)
				),
				testCase(
						"([C)Ljava/lang/String;",
						new Expected(
								MemberType.METHOD,
								"",
								"",
								"",
								"([C)Ljava/lang/String;",
								"([C)Ljava/lang/String;"
						)
				),
				testCase(
						"<init>*",
						new Expected(
								null,
								"",
								"<init>",
								"*",
								"",
								"<init>*"
						)
				),
				testCase(
						"<init>*()V",
						new Expected(
								MemberType.METHOD,
								"",
								"<init>",
								"*",
								"()V",
								"<init>*()V"
						)
				),
				// https://github.com/FabricMC/tiny-remapper/issues/137
				testCase(
						"<init>*",
						new Expected(
								null,
								"",
								"<init>",
								"*",
								"",
								"<init>*"
						)
				),
				testCase(
						"*()Lcom/example/ExampleClass;",
						new Expected(
								MemberType.METHOD,
								"",
								"",
								"*",
								"()Lcom/example/ExampleClass;",
								"*()Lcom/example/ExampleClass;"
						)
				),
				testCase(
						" com/example/Owner . someMethod {1, 2} ()Z ",
						new Expected(
								MemberType.METHOD,
								"com/example/Owner",
								"someMethod",
								"{1, 2}",
								"()Z",
								"Lcom/example/Owner;someMethod{1, 2}()Z"
						)
				),

				// Nesting
				testCase(
						"outer -> *",
						new Expected(
								null,
								"",
								"outer",
								"",
								"",
								"outer -> *",
								""
						),
						new Expected(
								null,
								"",
								"",
								"*",
								"",
								"*"
						)
				),
				testCase(
						"outer ->* *",
						new Expected(
								null,
								"",
								"outer",
								"",
								"",
								"outer ->* *",
								"*"
						),
						new Expected(
								null,
								"",
								"",
								"*",
								"",
								"*"
						)
				),
				testCase(
						"outer ->{1, 2} ()V",
						new Expected(
								null,
								"",
								"outer",
								"",
								"",
								"outer ->{1, 2} ()V",
								"{1, 2}"
						),
						new Expected(
								MemberType.METHOD,
								"",
								"",
								"",
								"()V",
								"()V"
						)
				),
				testCase(
						"outer -> {1, 2}()V",
						new Expected(
								null,
								"",
								"outer",
								"",
								"",
								"outer -> {1, 2}()V",
								""
						),
						new Expected(
								MemberType.METHOD,
								"",
								"",
								"{1, 2}",
								"()V",
								"{1, 2}()V"
						)
				),
				testCase(
						"* ->* * ->* *",
						new Expected(
								null,
								"",
								"",
								"*",
								"",
								"* ->* * ->* *",
								"*"
						),
						new Expected(
								null,
								"",
								"",
								"*",
								"",
								"* ->* *",
								"*"
						),
						new Expected(
								null,
								"",
								"",
								"*",
								"",
								"*"
						)
				),
				testCase(
						"* ->+ Ljava/lang/Runnable;run()V",
						new Expected(
								null,
								"",
								"",
								"*",
								"",
								"* ->+ Ljava/lang/Runnable;run()V",
								"+"
						),
						new Expected(
								MemberType.METHOD,
								"java/lang/Runnable",
								"run",
								"",
								"()V",
								"Ljava/lang/Runnable;run()V"
						)
				)
		);
	}

	private static Arguments testCase(String input, Expected... expectedParts) {
		return Arguments.of(input, expectedParts);
	}

	private static class Expected {
		private final MemberType expectedType;
		private final String expectedOwner;
		private final String expectedName;
		private final String expectedQuantifier;
		private final String expectedDesc;
		private final String expectedToString;
		private final String expectedNextDepth;

		Expected(
				MemberType expectedType, String expectedOwner, String expectedName, String expectedQuantifier,
				String expectedDesc, String expectedToString
		) {
			this(expectedType, expectedOwner, expectedName, expectedQuantifier, expectedDesc, expectedToString, null);
		}

		Expected(
				MemberType expectedType, String expectedOwner, String expectedName, String expectedQuantifier,
				String expectedDesc, String expectedToString, String expectedNextDepth
		) {
			this.expectedType = expectedType;
			this.expectedOwner = expectedOwner;
			this.expectedName = expectedName;
			this.expectedQuantifier = expectedQuantifier;
			this.expectedDesc = expectedDesc;
			this.expectedToString = expectedToString;
			this.expectedNextDepth = expectedNextDepth;
		}

		void check(MemberInfo info) {
			assertNotNull(info);
			assertEquals(expectedType, info.getType());
			assertEquals(expectedOwner, info.getOwner());
			assertEquals(expectedName, info.getName());
			assertEquals(expectedQuantifier, info.getQuantifier());
			assertEquals(expectedDesc, info.getDesc());
			assertEquals(expectedToString, info.toString());
			assertEquals(expectedNextDepth, info.getNextDepth());
		}
	}
}
