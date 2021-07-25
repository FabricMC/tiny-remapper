package net.fabricmc.tinyremapper.extension.mixin.hard.annotation;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.CamelPrefixString;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.ConvertibleMappable;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.IConvertibleString;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.PrefixString;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 */
public class InvokerAnnotationVisitor extends AnnotationVisitor {
	private final List<Consumer<CommonData>> tasks;
	private final MxMember method;
	private final List<String> targets;

	private boolean remap;
	private boolean isSoftTarget;

	public InvokerAnnotationVisitor(List<Consumer<CommonData>> tasks, AnnotationVisitor delegate, MxMember method, boolean remap, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);

		this.tasks = Objects.requireNonNull(tasks);
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
			tasks.add(data -> new InvokerMappable(data, method, targets).result());
		}

		super.visitEnd();
	}

	private static class InvokerMappable extends ConvertibleMappable {
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
		protected Collection<IConvertibleString> getPotentialNames() {
			return Arrays.asList(
					new CamelPrefixString(prefix, self.getName()),
					new PrefixString(prefix, self.getName()));
		}

		@Override
		protected String getDesc() {
			return self.getDesc();
		}
	}
}
