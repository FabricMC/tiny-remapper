package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.ClassVisitor;

/**
 * @see ClassVisitor#ClassVisitor(int, ClassVisitor)
 */
@FunctionalInterface
public interface WrapperFunction {
	// TODO: since TrEnvironment can get remapper, do we still need this remapper parameter?
	//  also need to rename the parameter name for TrEnvironment
	ClassVisitor wrap(ClassVisitor visitor, TrRemapper remapper, TrEnvironment classpath);

	default WrapperFunction andThen(WrapperFunction target) {
		return (visitor, remapper, classpath) -> target.wrap(this.wrap(visitor, remapper, classpath), remapper, classpath);
	}
}
