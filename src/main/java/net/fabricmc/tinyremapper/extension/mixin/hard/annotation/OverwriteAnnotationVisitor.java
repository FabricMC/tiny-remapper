package net.fabricmc.tinyremapper.extension.mixin.hard.annotation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.ConvertedMappable;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 */
public class OverwriteAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final TrMember method;
	private final List<String> targets;

	private boolean remap;

	public OverwriteAnnotationVisitor(CommonData data, AnnotationVisitor delegate, TrMember method, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);

		this.data = Objects.requireNonNull(data);
		this.method = Objects.requireNonNull(method);
		this.targets = Objects.requireNonNull(targets);

		this.remap = remap;
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP)) {
			remap = Objects.requireNonNull((Boolean) value);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (remap) {
			new OverwriteMappable(data, method, targets).result();
		}

		super.visitEnd();
	}

	private static class OverwriteMappable extends ConvertedMappable {
		OverwriteMappable(CommonData data, TrMember self, Collection<String> targets) {
			super(data, self, targets);
		}

		@Override
		protected String getConvertedName() {
			return self.getName();
		}

		@Override
		protected String getConvertedDesc() {
			return self.getDesc();
		}

		@Override
		protected String revertName(String name) {
			return name;
		}
	}
}
