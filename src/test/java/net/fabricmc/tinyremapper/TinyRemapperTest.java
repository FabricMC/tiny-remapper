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

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class TinyRemapperTest {
	@Test
	public void analyzeMrjVersion() throws ReflectiveOperationException {
		String input;
		String name;
		int result;

		name = "com/github/logicf/App";

		input = "/path/to/bin/META-INF/versions/16/com/github/logicf/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(16, result);

		input = "/META-INF/versions/9/com/github/logicf/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(9, result);

		input = "path/to/bin/META-INF/versions/16/com/github/logicf/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(16, result);

		input = "META-INF/versions/9/com/github/logicf/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(9, result);

		input = "/path/to/bin/com/github/logicf/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);

		input = "/path/to/bin/META-INF/versions/16/abc/com/github/logicf/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);

		input = "/path/to/bin/versions/16/com/github/logicf/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);

		input = "/META-INF/versions/9aa/com/github/logicf/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);

		input = "/bin/App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);

		input = "App.class";
		result = getMrjVersionFromPath(input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);
	}

	private static int getMrjVersionFromPath(String file, String name) throws ReflectiveOperationException {
		return (int) getMrjVersionFromPathMethod.invoke(null, Paths.get(file), name);
	}

	private static final Method getMrjVersionFromPathMethod;

	static {
		try {
			getMrjVersionFromPathMethod = TinyRemapper.class.getDeclaredMethod("analyzeMrjVersion", Path.class, String.class);
			getMrjVersionFromPathMethod.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
