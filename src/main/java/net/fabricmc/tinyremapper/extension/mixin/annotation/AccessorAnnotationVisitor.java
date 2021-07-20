package net.fabricmc.tinyremapper.extension.mixin.annotation;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;

import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;
import net.fabricmc.tinyremapper.extension.mixin.annotation.common.FirstPassAnnotationVisitor;
import net.fabricmc.tinyremapper.extension.mixin.common.LoggerOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;

/**
 * Required order: {@code remap} [{@code value} | emit]
 * <p>Pass 1: read remap.</p>
 * <p>Pass 2: read value; remap value or emit mapping.</p>
 */
public class AccessorAnnotationVisitor extends AccessorFirstPassAnnotationVisitor {
	public AccessorAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(data, remap, targets);
	}
}

class AccessorFirstPassAnnotationVisitor extends FirstPassAnnotationVisitor {
	private final CommonDataHolder data;
	private final List<String> targets;

	AccessorFirstPassAnnotationVisitor(CommonDataHolder data, boolean remap, List<String> targets) {
		super(Annotation.ACCESSOR, remap);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visitEnd() {
		if (super.remap) {
			this.accept(new AccessorSecondPassAnnotationVisitor(this.data, this.targets));
		} else {
			this.accept(this.data.delegate);
		}

		super.visitEnd();
	}
}

class AccessorSecondPassAnnotationVisitor extends AnnotationVisitor {
	private final CommonDataHolder data;
	private final List<String> targets;
	private final String fieldDesc;

	private String value;

	private static final Pattern GETTER_PATTERN = Pattern.compile("(?<=\\(\\)).*");
	private static final Pattern SETTER_PATTERN = Pattern.compile("(?<=\\().*(?=\\)V)");

	AccessorSecondPassAnnotationVisitor(CommonDataHolder data, List<String> targets) {
		super(Constant.ASM_VERSION, data.delegate);
		this.data = Objects.requireNonNull(data);
		this.targets = Objects.requireNonNull(targets);

		Matcher getterMatcher = GETTER_PATTERN.matcher(data.memberDesc);
		Matcher setterMatcher = SETTER_PATTERN.matcher(data.memberDesc);

		if (getterMatcher.find()) {
			this.fieldDesc = getterMatcher.group();
		} else if (setterMatcher.find()) {
			this.fieldDesc = setterMatcher.group();
		} else {
			throw new RuntimeException(data.memberDesc + " is not getter or setter");
		}
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.VALUE.get())) {
			AccessorSecondPassAnnotationVisitor.this.value = Objects.requireNonNull((String) value);

			String srcName = AccessorSecondPassAnnotationVisitor.this.value;
			String srcDesc = AccessorSecondPassAnnotationVisitor.this.fieldDesc;

			String dstName = CommonUtility.remap(
					data.remapper, AnnotationType.FIELD,
					AccessorSecondPassAnnotationVisitor.this.targets, srcName, srcDesc);

			if (srcName.equals(dstName) && !Constant.UNMAP_NAMES.contains(srcName)) {
				LoggerOld.remapFail("@Accessor", AccessorSecondPassAnnotationVisitor.this.targets, data.className, srcName);
			}

			value = dstName;
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		if (AccessorSecondPassAnnotationVisitor.this.value.isEmpty()) {
			// only remap hard-target if no value is specify
			String prefix;

			if (data.memberName.startsWith("get")) {
				prefix = "get";
			} else if (data.memberName.startsWith("set")) {
				prefix = "set";
			} else if (data.memberName.startsWith("is")) {
				prefix = "is";
			} else {
				throw new RuntimeException(data.memberName + " does not start with get, set or is.");
			}

			String srcName = CommonUtility.removeCamelPrefix(prefix, data.memberName);
			String srcDesc = AccessorSecondPassAnnotationVisitor.this.fieldDesc;

			String dstName = CommonUtility.remap(
					data.remapper, AnnotationType.FIELD,
					AccessorSecondPassAnnotationVisitor.this.targets, srcName, srcDesc);

			if (srcName.equals(dstName) && !Constant.UNMAP_NAMES.contains(srcName)) {
				LoggerOld.remapFail("@Accessor", AccessorSecondPassAnnotationVisitor.this.targets, data.className, srcName);
			} else {
				srcName = data.memberName;
				srcDesc = data.memberDesc;
				dstName = CommonUtility.addCamelPrefix(prefix, dstName);

				CommonUtility.emit(
						data.remapper, AnnotationType.METHOD, data.mapping,
						data.className, srcName, srcDesc, dstName);
			}
		}

		super.visitEnd();
	}
}
