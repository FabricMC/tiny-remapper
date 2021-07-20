package net.fabricmc.tinyremapper.extension.mixin.annotation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.FirstPassAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.common.LoggerOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;

/**
 * Required order: {@code remap} [{@code value} | {@code targets}]
 * <p>Pass 1: read remap.</p>
 * <p>Pass 2: read targets & value; remap targets.</p>
 */
public class MixinAnnotationVisitor extends MixinFirstPassAnnotationVisitor {
	public MixinAnnotationVisitor(CommonDataHolder data, AtomicBoolean remapOut, List<String> targetsOut) {
		super(data, remapOut, targetsOut);
	}
}

class MixinFirstPassAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonDataHolder data;
	private final AtomicBoolean remap0;
	private final List<String> targets;

	MixinFirstPassAnnotationVisitor(CommonDataHolder data, AtomicBoolean remapOut, List<String> targetsOut) {
		super(Annotation.MIXIN, true);
		this.data = Objects.requireNonNull(data);
		this.remap0 = Objects.requireNonNull(remapOut);
		this.targets = Objects.requireNonNull(targetsOut);
	}

	@Override
	public void visitEnd() {
		this.remap0.set(super.remap);

		// The second pass is needed regardless of remap, because it may have
		// children annotation need to remap.
		this.accept(new MixinSecondPassAnnotationVisitor(this.data, super.remap, this.targets));

		super.visitEnd();
	}
}

class MixinSecondPassAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolder data;
	private final boolean remap;
	private final List<String> targets;

	MixinSecondPassAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targetsOut) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.remap = remap;
		this.targets = Objects.requireNonNull(targetsOut);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor visitor = super.visitArray(name);

		if (name.equals(AnnotationElement.TARGETS.get())) {
			return new AnnotationVisitor(Constant.ASM_VERSION, visitor) {
				@Override
				public void visit(String name, Object value) {
					String srcName = Objects.requireNonNull((String) value).replace(".", "/");
					String dstName = srcName;

					MixinSecondPassAnnotationVisitor.this.targets.add(srcName);

					if (remap) {
						dstName = data.remapper.map(srcName);

						if (srcName.equals(dstName)) {
							LoggerOld.remapFail("@Mixin", srcName, data.className);
						}
					}

					value = dstName;
					super.visit(name, value);
				}
			};
		} else if (name.equals(AnnotationElement.VALUE.get())) {
			return new AnnotationVisitor(Constant.ASM_VERSION, visitor) {
				@Override
				public void visit(String name, Object value) {
					Type srcType = Objects.requireNonNull((Type) value);
					MixinSecondPassAnnotationVisitor.this.targets.add(srcType.getInternalName());

					super.visit(name, value);
				}
			};
		} else {
			return visitor;
		}
	}
}
