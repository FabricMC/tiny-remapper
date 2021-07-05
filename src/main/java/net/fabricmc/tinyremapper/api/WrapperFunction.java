package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.ClassVisitor;

/**
 * @see ClassVisitor#ClassVisitor(int, ClassVisitor)
 */
@FunctionalInterface
public interface WrapperFunction {
	ClassVisitor wrap(ClassVisitor visitor, ExtendedRemapper remapper, Classpath classpath);
}
