package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.ClassVisitor;

/**
 * @see ClassVisitor#ClassVisitor(int, ClassVisitor)
 */
@FunctionalInterface
public interface WrapperFunction {
	ClassVisitor wrap(ClassVisitor visitor, ExtendedRemapper remapper, TrEnvironment classpath);

	default WrapperFunction andThen(WrapperFunction target) {
		return (visitor, remapper, classpath) -> target.wrap(this.wrap(visitor, remapper, classpath), remapper, classpath);
	}
}
