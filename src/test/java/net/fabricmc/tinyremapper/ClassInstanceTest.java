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

package net.fabricmc.tinyremapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

public class ClassInstanceTest {
	@Test
	void addInputTags() {
		InputTag tag1 = new InputTag();
		InputTag tag2 = new InputTag();

		ClassInstance classInstance = new ClassInstance(null, false, new InputTag[]{}, null, new byte[]{});
		assertEquals(0, classInstance.getInputTags().length);

		classInstance.addInputTags(new InputTag[]{tag1});
		assertEquals(1, classInstance.getInputTags().length);
		assertContains(tag1, classInstance.getInputTags());

		classInstance.addInputTags(new InputTag[]{tag1});
		assertEquals(1, classInstance.getInputTags().length);
		assertContains(tag1, classInstance.getInputTags());

		classInstance.addInputTags(new InputTag[]{tag2});
		assertEquals(2, classInstance.getInputTags().length);
		assertContains(tag1, classInstance.getInputTags());
		assertContains(tag2, classInstance.getInputTags());
	}

	private static <T> void assertContains(T value, T[] array) {
		for (T t : array) {
			if (t == value) {
				return;
			}
		}

		fail("Array does not contain value: " + value);
	}
}
