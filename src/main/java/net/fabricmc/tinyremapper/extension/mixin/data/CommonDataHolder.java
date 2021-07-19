package net.fabricmc.tinyremapper.extension.mixin.data;

import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.api.TrEnvironment;

public final class CommonDataHolder {
	public final Remapper remapper;
	public final TrEnvironment environment;
	public final AnnotationVisitor delegate;
	public final IMappingHolder mapping;

	public final AnnotationType type;
	public final String className;
	public final String memberName;
	public final String memberDesc;

	/**
	 * Used for method or field annotation visitor.
	 */
	public CommonDataHolder(Remapper remapper, TrEnvironment environment,
							AnnotationVisitor delegate, IMappingHolder mapping, AnnotationType type,
							String className, String memberName, String memberDesc) {
		this.remapper = Objects.requireNonNull(remapper);
		this.environment = environment;
		this.delegate = delegate;
		this.mapping = Objects.requireNonNull(mapping);

		this.type = type;
		this.className = className;
		this.memberName = memberName;
		this.memberDesc = memberDesc;
	}

	/**
	 * Used for class visitor, method visitor or field visitor.
	 */
	public CommonDataHolder(Remapper remapper, TrEnvironment environment, IMappingHolder mapping) {
		this(remapper, environment, null, mapping, null, null, null, null);
	}

	public CommonDataHolder addClassName(String className) {
		assert this.className == null;
		return new CommonDataHolder(remapper, environment, delegate, mapping, type, className, memberName, memberDesc);
	}

	public CommonDataHolder addMember(String memberName, String memberDesc) {
		assert this.memberName == null;
		assert this.memberDesc == null;
		return new CommonDataHolder(remapper, environment, delegate, mapping, type, className, memberName, memberDesc);
	}

	public CommonDataHolder addAnnotation(AnnotationVisitor delegate, AnnotationType type) {
		assert this.delegate == null;
		assert this.type == null;
		return new CommonDataHolder(remapper, environment, delegate, mapping, type, className, memberName, memberDesc);
	}

	public CommonDataHolder alterAnnotationVisitor(AnnotationVisitor delegate) {
		assert this.delegate != null;
		return new CommonDataHolder(remapper, environment, delegate, mapping, type, className, memberName, memberDesc);
	}
}
