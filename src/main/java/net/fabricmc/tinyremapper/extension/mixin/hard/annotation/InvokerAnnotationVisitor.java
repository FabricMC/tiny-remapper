package net.fabricmc.tinyremapper.extension.mixin.hard.annotation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.ConvertedMappable;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 */
public class InvokerAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final MxMember method;
	private final List<String> targets;

	private boolean remap;
	private boolean isSoftTarget;

	public InvokerAnnotationVisitor(CommonData data, AnnotationVisitor delegate, MxMember method, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);

		this.data = Objects.requireNonNull(data);
		this.method = Objects.requireNonNull(method);
		this.targets = Objects.requireNonNull(targets);

		this.remap = remap;
		this.isSoftTarget = false;
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.REMAP)) {
			remap = Objects.requireNonNull((Boolean) value);
		} else if (name.equals(AnnotationElement.VALUE)) {
			isSoftTarget = true;
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (remap && !isSoftTarget) {
			new InvokerMappable(data, method, targets).result();
		}

		super.visitEnd();
	}

	private static class InvokerMappable extends ConvertedMappable {
		private final String prefix;

		InvokerMappable(CommonData data, MxMember self, Collection<String> targets) {
			super(data, self, targets);

			if (self.getName().startsWith("call")) {
				this.prefix = "call";
			} else if (self.getName().startsWith("invoke")) {
				this.prefix = "invoke";
			} else {
				throw new RuntimeException(self.getName() + " does not start with call or invoke.");
			}
		}

		@Override
		protected String getConvertedName() {
			return StringUtility.removeCamelPrefix(prefix, self.getName());
		}

		@Override
		protected String getConvertedDesc() {
			return self.getDesc();
		}

		@Override
		protected String revertName(String name) {
			return StringUtility.addCamelPrefix(prefix, name);
		}
	}
}
