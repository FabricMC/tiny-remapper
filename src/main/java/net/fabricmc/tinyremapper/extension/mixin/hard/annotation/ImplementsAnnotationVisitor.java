package net.fabricmc.tinyremapper.extension.mixin.hard.annotation;

import java.util.List;
import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.hard.data.SoftInterface;
import net.fabricmc.tinyremapper.extension.mixin.hard.data.SoftInterface.Remap;

/**
 * For multiple interfaces, if multiple match detected for a method, and result a
 * mapping conflict, a warning will appear and will use the first name (This
 * behaviour is disallowed in Mixin AP).
 */
public class ImplementsAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final List<SoftInterface> interfaces;

	public ImplementsAnnotationVisitor(CommonData data, AnnotationVisitor delegate, List<SoftInterface> interfacesOut) {
		super(Constant.ASM_VERSION, delegate);

		this.data = Objects.requireNonNull(data);
		this.interfaces = Objects.requireNonNull(interfacesOut);
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		AnnotationVisitor av = super.visitArray(name);

		if (name.equals(AnnotationElement.VALUE)) {
			return new AnnotationVisitor(Constant.ASM_VERSION, av) {
				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (!descriptor.equals(Annotation.INTERFACE)) {
						throw new RuntimeException("Unexpected annotation " + descriptor);
					}

					AnnotationVisitor av1 = super.visitAnnotation(name, descriptor);

					SoftInterface _interface = new SoftInterface();
					interfaces.add(_interface);

					return new InterfaceAnnotationVisitor(data, av1, _interface);
				}
			};
		} else {
			return av;
		}
	}

	/*public static void visitMethod(CommonData data, TrMember method, List<SoftInterface> interfaces) {
		List<Pair<String, TrMember>> candidates = new ArrayList<>();

		for (SoftInterface _interface : interfaces) {
			TrClass target = _interface.getTarget();
			String prefix = _interface.getPrefix();
			Remap remap = _interface.getRemap();

			// ONLY_PREFIX or ALL or FORCE, and the method start with the prefix.
			if (remap.compareTo(Remap.ONLY_PREFIX) >= 0 && method.getName().startsWith(prefix)) {
				TrMember resolve = target.resolveMethod(method.getName().substring(prefix.length()), method.getDesc());

				if (resolve == null) {
					data.logger.unresolved(method.getOwner().getName(), target.getName(), method.getName().substring(prefix.length()));
				} else {
					candidates.add(Pair.of(prefix, resolve));
				}
			}

			// ALL or FORCE
			if (remap.compareTo(Remap.ALL) >= 0) {
				TrMember resolve = target.resolveMethod(method.getName(), method.getDesc());

				if (resolve == null) {
					if (remap.compareTo(Remap.FORCE) >= 0) {
						data.logger.unresolved(method.getOwner().getName(), target.getName(), method.getName());
					}
				} else {
					candidates.add(Pair.of("", resolve));
				}
			}
		}

		// This is to guarantee the insertion order
		LinkedHashSet<String> list = candidates.stream()
				.map(candidate ->
						candidate.first() + data.remapper.map(candidate.second()))
				.collect(Collectors.toCollection(LinkedHashSet::new));

		if (list.size() > 1) {
			data.logger.conflict(method.getOwner().getName(), method.getName(), list);
		}

		list.stream().findFirst().ifPresent(
				dstName -> data.mapping.putMethod(method.getOwner().getName(), method.getName(), method.getDesc(), dstName));
	}*/
}

class InterfaceAnnotationVisitor extends AnnotationVisitor {
	private final CommonData data;
	private final SoftInterface _interface;

	InterfaceAnnotationVisitor(CommonData data, AnnotationVisitor delegate, SoftInterface _interfaceOut) {
		super(Constant.ASM_VERSION, delegate);

		this.data = Objects.requireNonNull(data);
		this._interface = Objects.requireNonNull(_interfaceOut);

		this._interface.setRemap(Remap.ALL);	// default value
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.IFACE)) {
			Type target = Objects.requireNonNull((Type) value);
			this._interface.setTarget(data.environment.getClass(target.getInternalName()));
		} else if (name.equals(AnnotationElement.PREFIX)) {
			String prefix = Objects.requireNonNull((String) value);
			this._interface.setPrefix(prefix);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnum(String name, String descriptor, String value) {
		if (name.equals(AnnotationElement.REMAP)) {
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
