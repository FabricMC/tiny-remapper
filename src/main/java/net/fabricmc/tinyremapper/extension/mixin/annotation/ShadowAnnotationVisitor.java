package net.fabricmc.tinyremapper.extension.mixin.annotation;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;

/**
 * Required order: [{@code remap} | {@code prefix}] emit.
 * <p>Pass 1: read remap & prefix, and then emit</p>
 */
public class ShadowAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolder data;
	private final List<String> targets;

	private boolean remap;
	private String prefix;

	public ShadowAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
		this.remap = remap;
		this.prefix = "shadow$";
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP.get())) {
			remap = Objects.requireNonNull((Boolean) value);
		} else if (name.equals(AnnotationElement.PREFIX.get())) {
			prefix = Objects.requireNonNull((String) value);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (remap) {
			// Generate mappings
			String srcName = data.memberName.startsWith(prefix)
					? data.memberName.substring(prefix.length()) : data.memberName;
			String srcDesc = data.memberDesc;

			String dstName = CommonUtility.remap(
					data.remapper, data.type, ShadowAnnotationVisitor.this.targets,
					srcName, srcDesc);

			if (srcName.equals(dstName) && !Constant.UNMAP_NAMES.contains(srcName)) {
				Logger.remapFail("@Shadow", ShadowAnnotationVisitor.this.targets, data.className, srcName);
			} else {
				srcName = data.memberName;
				dstName = data.memberName.startsWith(prefix) ? prefix + dstName : dstName;
				CommonUtility.emit(
						data.remapper, data.type, data.mapping,
						data.className, srcName, srcDesc, dstName);
			}
		}

		super.visitEnd();
	}
}
