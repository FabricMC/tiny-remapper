package net.fabricmc.tinyremapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TinyRemapperTest {
	@Test
	public void getMrjVersionFromPath()
			throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		Method getMrjVersionFromPathMethod = TinyRemapper.class.getDeclaredMethod("getMrjVersionFromPath", String.class);
		getMrjVersionFromPathMethod.setAccessible(true);

		String input;
		int result;

		input = "/path/to/bin/META-INF/versions/16/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input);
		assertEquals(16, result);

		input = "/META-INF/versions/9/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input);
		assertEquals(9, result);

		input = "path/to/bin/com/github/logicf/App.class";
		result = (Integer) getMrjVersionFromPathMethod.invoke(null, input);
		assertEquals(ClassInstance.MRJ_DEFAULT, result);
	}
}