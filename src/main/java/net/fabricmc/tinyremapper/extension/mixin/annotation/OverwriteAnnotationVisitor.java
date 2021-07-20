package net.fabricmc.tinyremapper.extension.mixin.annotation;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.LoggerOld;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;

/**
 * Required order: {@code remap} emit.
 * <p>Pass 1: read remap and then emit</p>
 */
public class OverwriteAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolder data;
	private final List<String> targets;

	private boolean remap;

	public OverwriteAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
		this.remap = remap;
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP.get())) {
			remap = Objects.requireNonNull((Boolean) value);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (remap) {
			// Generate mappings
			String srcName = data.memberName;
			String srcDesc = data.memberDesc;

			String dstName = CommonUtility.remap(
					data.remapper, AnnotationType.METHOD,
					OverwriteAnnotationVisitor.this.targets, srcName, srcDesc);

			if (srcName.equals(dstName) && !Constant.UNMAP_NAMES.contains(srcName)) {
				LoggerOld.remapFail("@Overwrite", OverwriteAnnotationVisitor.this.targets, data.className, srcName);
			} else {
				CommonUtility.emit(
						data.remapper, AnnotationType.METHOD, data.mapping,
						data.className, srcName, srcDesc, dstName);
			}
		}

		super.visitEnd();
	}
}
