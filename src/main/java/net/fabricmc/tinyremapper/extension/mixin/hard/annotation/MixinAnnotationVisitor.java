package net.fabricmc.tinyremapper.extension.mixin.hard.annotation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;

/**
 * Only collect information for other hard-target.
 */
public class MixinAnnotationVisitor extends AnnotationVisitor {
	private final AtomicBoolean remap0;
	private final List<String> targets;

	public MixinAnnotationVisitor(AnnotationVisitor delegate, AtomicBoolean remapOut, List<String> targetsOut) {
		super(Constant.ASM_VERSION, delegate);

		this.remap0 = Objects.requireNonNull(remapOut);
		this.targets = Objects.requireNonNull(targetsOut);

		this.remap0.set(true);	// default value is true.
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP)) {
			remap0.set(Objects.requireNonNull((Boolean) value));
		}

		super.visit(name, value);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor visitor = super.visitArray(name);

		if (name.equals(AnnotationElement.TARGETS)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, visitor) {
				@Override
				public void visit(String name, Object value) {
					String srcName = ((String) value).replaceAll("\\s", "").replace('.', '/');
					String dstName = srcName;

					MixinAnnotationVisitor.this.targets.add(srcName);

					value = dstName;
					super.visit(name, value);
				}
			};
		} else if (name.equals(AnnotationElement.VALUE)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, visitor) {
				@Override
				public void visit(String name, Object value) {
					Type srcType = Objects.requireNonNull((Type) value);

					MixinAnnotationVisitor.this.targets.add(srcType.getInternalName());

					super.visit(name, value);
				}
			};
		} else {
			return visitor;
		}
	}
}
