package net.fabricmc.tinyremapper.extension.mixin.hard.annotation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.ConvertedMappable;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 */
public class AccessorAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final TrMember method;
	private final List<TrClass> targets;

	private boolean remap;
	private boolean isSoftTarget;

	public AccessorAnnotationVisitor(CommonData data, AnnotationVisitor delegate, TrMember method, boolean remap, List<TrClass> targets) {
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
			IMappable<Void> mappable = new AccessorMappable(data, method, targets);
			mappable.result();
		}

		super.visitEnd();
	}

	private static class AccessorMappable extends ConvertedMappable {
		private final String prefix;
		private final String fieldDesc;

		private static final Pattern GETTER_PATTERN = Pattern.compile("(?<=\\(\\)).*");
		private static final Pattern SETTER_PATTERN = Pattern.compile("(?<=\\().*(?=\\)V)");

		AccessorMappable(CommonData data, TrMember self, Collection<TrClass> targets) {
			super(data, self, targets);

			if (self.getName().startsWith("get")) {
				this.prefix = "get";
			} else if (self.getName().startsWith("set")) {
				this.prefix = "set";
			} else if (self.getName().startsWith("is")) {
				this.prefix = "is";
			} else {
				throw new RuntimeException(self.getName() + " does not start with get, set or is.");
			}

			Matcher getterMatcher = GETTER_PATTERN.matcher(self.getDesc());
			Matcher setterMatcher = SETTER_PATTERN.matcher(self.getDesc());

			if (getterMatcher.find()) {
				this.fieldDesc = getterMatcher.group();
			} else if (setterMatcher.find()) {
				this.fieldDesc = setterMatcher.group();
			} else {
				throw new RuntimeException(self.getDesc() + " is not getter or setter");
			}
		}

		@Override
		protected String getConvertedName() {
			return StringUtility.removeCamelPrefix(prefix, self.getName());
		}

		@Override
		protected String getConvertedDesc() {
			return fieldDesc;
		}

		@Override
		protected String revertName(String name) {
			return StringUtility.addCamelPrefix(prefix, name);
		}
	}
}
