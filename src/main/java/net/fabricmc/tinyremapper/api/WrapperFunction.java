package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.ClassVisitor;

/**
 * @see ClassVisitor#ClassVisitor(int, ClassVisitor)
 */
public interface WrapperFunction {
	ClassVisitor wrap(ClassVisitor visitor, ExtendedRemapper remapper, Classpath classpath);
}
