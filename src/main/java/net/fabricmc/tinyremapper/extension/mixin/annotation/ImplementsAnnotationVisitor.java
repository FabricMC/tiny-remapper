package net.fabricmc.tinyremapper.extension.mixin.annotation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.extension.mixin.annotation.ImplementsAnnotationVisitor.Interface;
import net.fabricmc.tinyremapper.extension.mixin.annotation.ImplementsAnnotationVisitor.Interface.Remap;
import net.fabricmc.tinyremapper.extension.mixin.annotation.common.CommonUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.LoggerOld;
import net.fabricmc.tinyremapper.extension.mixin.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.CommonDataHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.data.IMappingHolder;

public class ImplementsAnnotationVisitor extends AnnotationVisitor {
	public static class Interface {
		public enum Remap {
			ALL, FORCE, NONE, ONLY_PREFIX
		}

		private String target;
		private String prefix;
		private Remap remap;

		private String target() {
			return target;
		}

		private String prefix() {
			return prefix;
		}

		private Remap remap() {
			return remap;
		}

		void setTarget(String target) {
			this.target = target;
		}

		void setPrefix(String prefix) {
			this.prefix = prefix;
		}

		void setRemap(Remap remap) {
			this.remap = remap;
		}

		@Override
		public String toString() {
			return "Interface{"
					+ "target='" + target + '\''
					+ ", prefix='" + prefix + '\''
					+ ", remap=" + remap
					+ '}';
		}
	}

	private final List<Interface> interfaces;

	public ImplementsAnnotationVisitor(CommonDataHolder data, List<Interface> interfacesOut) {
		super(Constant.ASM_VERSION, data.delegate);
		this.interfaces = Objects.requireNonNull(interfacesOut);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor annotationVisitor = super.visitArray(name);

		if (name.equals(AnnotationElement.VALUE.get())) {
			return new AnnotationVisitor(Constant.ASM_VERSION, annotationVisitor) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (!descriptor.equals(Annotation.INTERFACE)) {
						throw new RuntimeException("Unexpected annotation " + descriptor);
					}

					AnnotationVisitor annotationVisitor1 = super.visitAnnotation(name, descriptor);

					Interface _interface = new Interface();
					interfaces.add(_interface);

					return new InterfaceAnnotationVisitor(annotationVisitor1, _interface);
				}
			};
		} else {
			return annotationVisitor;
		}
	}

	public static void visitMethod(CommonDataHolder data, List<Interface> interfaces) {
		Remapper remapper = Objects.requireNonNull(data.remapper);
		TrEnvironment environment = Objects.requireNonNull(data.environment);
		IMappingHolder mapping = Objects.requireNonNull(data.mapping);
		String owner = Objects.requireNonNull(data.className);
		String methodName = Objects.requireNonNull(data.memberName);
		String methodDesc = Objects.requireNonNull(data.memberDesc);

		for (Interface _interface : interfaces) {
			String target = _interface.target();
			String prefix = _interface.prefix();
			Remap remap = _interface.remap();

			TrClass targetClass = environment.getClass(target);

			if (methodName.startsWith(prefix)) {		// handle prefix member
				String srcName = methodName.substring(prefix.length());
				String srcDesc = methodDesc;

				if (targetClass.resolveMethod(srcName, srcDesc) != null) {	// can find this method in target
					if (remap.equals(Remap.ONLY_PREFIX) || remap.equals(Remap.ALL) || remap.equals(Remap.FORCE)) {
						String dstName = CommonUtility.remap(
								remapper, AnnotationType.METHOD, target,
								srcName, srcDesc);

						if (srcName.equals(dstName) && !Constant.UNMAP_NAMES.contains(srcName)) {    // no mapping found
							LoggerOld.remapFail("@Implements", Collections.singletonList(target), owner, srcName);
						} else { // continue process
							srcName = methodName;
							dstName = prefix + dstName;

							CommonUtility.emit(
									remapper, AnnotationType.METHOD, mapping,
									owner, srcName, srcDesc, dstName);
						}
					}
				} else {	// cannot find this method in target
					LoggerOld.warn("Interface " + target + " does not contains method " + srcName + ", " + srcDesc);
				}
			} else {	// handle non-prefix member
				String srcName = methodName;
				String srcDesc = methodDesc;

				if (targetClass.resolveMethod(srcName, srcDesc) != null) {	// can find this method in target
					if (remap.equals(Remap.ALL) || remap.equals(Remap.FORCE)) {
						String dstName = CommonUtility.remap(
								remapper, AnnotationType.METHOD, target,
								srcName, srcDesc);

						if (srcName.equals(dstName) && !Constant.UNMAP_NAMES.contains(srcName)) {
							if (remap.equals(Remap.FORCE)) {
								LoggerOld.remapFail("@Implements", Collections.singletonList(target), owner, srcName);
							}
						} else {
							CommonUtility.emit(
									remapper, AnnotationType.METHOD, mapping,
									owner, srcName, srcDesc, dstName);
						}
					}
				}
			}
		}
	}
}

class InterfaceAnnotationVisitor extends AnnotationVisitor {
	private final Interface _interface;

	InterfaceAnnotationVisitor(AnnotationVisitor delegate, Interface _interfaceOut) {
		super(Constant.ASM_VERSION, delegate);
		this._interface = Objects.requireNonNull(_interfaceOut);

		this._interface.setRemap(Remap.ALL);	// default value
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.IFACE.get())) {
			Type target = Objects.requireNonNull((Type) value);
			this._interface.setTarget(target.getInternalName());
		} else if (name.equals(AnnotationElement.PREFIX.get())) {
			String prefix = Objects.requireNonNull((String) value);
			this._interface.setPrefix(prefix);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnum(String name, String descriptor, String value) {
		if (name.equals(AnnotationElement.REMAP.get())) {
			if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Interface$Remap;")) {
				throw new RuntimeException("Incorrect enum type of Interface.Remap " + descriptor);
			}

			for (Remap candidate : Remap.values()) {
				if (candidate.name().equals(value)) {
					this._interface.setRemap(candidate);
					break;
				}
			}
		}

		super.visitEnum(name, descriptor, value);
	}
}
