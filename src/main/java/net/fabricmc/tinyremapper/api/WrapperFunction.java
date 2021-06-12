package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.ClassVisitor;

public interface WrapperFunction {
	ClassVisitor wrap(ClassVisitor visitor, ExtendedRemapper remapper, Classpath classpath);
}
