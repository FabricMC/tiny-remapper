package net.fabricmc.tinyremapper.extension.mixin.hard.annotation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility;
import net.fabricmc.tinyremapper.extension.mixin.common.Resolver;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Pair;
import net.fabricmc.tinyremapper.extension.mixin.hard.data.SoftInterface;
import net.fabricmc.tinyremapper.extension.mixin.hard.data.SoftInterface.Remap;
import net.fabricmc.tinyremapper.extension.mixin.hard.util.HardTargetMappable;

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

	public static void visitMethod(CommonData data, TrMember method, List<SoftInterface> interfaces) {
		new SoftImplementsMappable(data, method, interfaces).result();
	}

	private static class SoftImplementsMappable extends HardTargetMappable {
		private final Collection<SoftInterface> interfaces;

		SoftImplementsMappable(CommonData data, TrMember self, Collection<SoftInterface> interfaces) {
			super(data, self);

			this.interfaces = Objects.requireNonNull(interfaces);
		}

		@Override
		protected Optional<String> getNewName() {
			final Resolver resolver = new Resolver(data.logger);
			final MapUtility mapper = new MapUtility(data.remapper, data.logger);

			Stream<String> stream = Stream.empty();

			stream = Stream.concat(stream, interfaces.stream()
					.filter(iface -> iface.getRemap().compareTo(Remap.ONLY_PREFIX) >= 0)	// select the interface with ONLY_PREFIX, ALL, or FORCE
					.filter(iface -> self.getName().startsWith(iface.getPrefix()))    		// select the interfaces matches the prefix
					.map(iface -> Pair.of(
							iface.getPrefix(),
							resolver.resolve(
									iface.getTarget(),
									self.getName().substring(iface.getPrefix().length()),
									self.getDesc(),
									Resolver.FLAG_UNIQUE | Resolver.FLAG_RECURSIVE
							)
					))																		// resolve the method to target method
					.filter(pair -> pair.second().isPresent())
					.map(pair -> Pair.of(pair.first(), pair.second().get()))
					.map(pair -> Pair.of(pair.first(), mapper.map(pair.second())))			// remap the target method
					.map(pair -> pair.first() + pair.second()));							// add back prefix

			stream = Stream.concat(stream, interfaces.stream()
					.filter(iface -> iface.getRemap().compareTo(Remap.ALL) >= 0)			// select the interface with ALL, or FORCE
					.map(iface -> resolver.resolve(iface.getTarget(), self.getName(), self.getDesc(), Resolver.FLAG_UNIQUE | Resolver.FLAG_RECURSIVE))
					.filter(Optional::isPresent)
					.map(Optional::get)
					.map(mapper::map));

			List<String> collection = stream.distinct().collect(Collectors.toList());

			if (collection.size() > 1) {
				data.logger.error("Conflict mapping detected, " + self.getName() + " -> " + collection);
			} else if (collection.isEmpty()) {
				data.logger.error("Cannot remap " + self.getName() + " because it does not exists in any of the interfaces " + interfaces);
			}

			return collection.stream().findFirst();
		}
	}

	private static class InterfaceAnnotationVisitor extends AnnotationVisitor {
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
}
