package net.fabricmc.tinyremapper.api;

import java.util.Arrays;

import org.objectweb.asm.ClassVisitor;

/**
 * @see ClassVisitor#ClassVisitor(int, ClassVisitor)
 */
@FunctionalInterface
public interface WrapperFunction {
	static WrapperFunction combine(WrapperFunction... functions) {
		return combine(Arrays.asList(functions));
	}

	static WrapperFunction combine(Iterable<WrapperFunction> functions) {
		return (visitor, remapper, classpath) -> {
			ClassVisitor current = visitor;

			for (WrapperFunction function : functions) {
				current = function.wrap(current, remapper, classpath);
			}

			return current;
		};
	}

	ClassVisitor wrap(ClassVisitor visitor, ExtendedRemapper remapper, Classpath classpath);

	default WrapperFunction andThen(WrapperFunction target) {
		return (visitor, remapper, classpath) -> target.wrap(this.wrap(visitor, remapper, classpath), remapper, classpath);
	}
}
