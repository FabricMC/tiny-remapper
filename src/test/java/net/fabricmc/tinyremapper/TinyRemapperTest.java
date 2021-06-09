package net.fabricmc.tinyremapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TinyRemapperTest {
	@Test
	public void analyzeMrjVersion()
			throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		Method getMrjVersionFromPathMethod = TinyRemapper.class.getDeclaredMethod("analyzeMrjVersion", String.class, String.class);
		getMrjVersionFromPathMethod.setAccessible(true);

		String input;
		String name;
		int result;

		name = "com/github/logicf/App";

		input = "/path/to/bin/META-INF/versions/16/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input, name);
		assertEquals(16, result);

		input = "/META-INF/versions/9/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input, name);
		assertEquals(9, result);

		input = "/path/to/bin/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);

		input = "/path/to/bin/META-INF/versions/16/abc/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);

		input = "/path/to/bin/versions/16/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);

		input = "/META-INF/versions/9aa/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input, name);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);
	}
}