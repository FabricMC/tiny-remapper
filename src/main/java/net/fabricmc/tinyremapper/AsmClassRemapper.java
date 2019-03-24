/*
 * Copyright (C) 2016, 2018 Player, asie
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnnotationRemapper;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.FieldRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;

class AsmClassRemapper extends ClassRemapper {

	private final boolean renameInvalidLocals;

	public AsmClassRemapper(ClassVisitor cv, AsmRemapper remapper, boolean renameInvalidLocals) {
		super(cv, remapper);
		this.renameInvalidLocals = renameInvalidLocals;
	}

	@Override
	protected FieldVisitor createFieldRemapper(FieldVisitor fieldVisitor) {
		return new AsmFieldRemapper(fieldVisitor, remapper);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor mv) {
		return new AsmMethodRemapper(mv, remapper, renameInvalidLocals);
	}

	private static class AsmFieldRemapper extends FieldRemapper {
		public AsmFieldRemapper(FieldVisitor fieldVisitor, Remapper remapper) {
			super(fieldVisitor, remapper);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			return AsmClassRemapper.createAsnAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
		}
	}

	private static class AsmMethodRemapper extends MethodRemapper {
		private final Map<String, Integer> nameCounts = new HashMap<>();
		private final boolean renameInvalidLocals;

		public AsmMethodRemapper(MethodVisitor methodVisitor, Remapper remapper, boolean renameInvalidLocals) {
			super(methodVisitor, remapper);
			this.renameInvalidLocals = renameInvalidLocals;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			return AsmClassRemapper.createAsnAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
		}


		@Override
		public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
			if (mv == null) return;

			descriptor = remapper.mapDesc(descriptor);

			if (renameInvalidLocals && !isValidJavaIdentifier(name)) {
				Type type = Type.getType(descriptor);
				boolean plural = false;

				if (type.getSort() == Type.ARRAY) {
					plural = true;
					type = type.getElementType();
				}

				String varName = type.getClassName();
				int dotIdx = varName.lastIndexOf('.');
				if (dotIdx != -1) varName = varName.substring(dotIdx + 1);

				varName = Character.toLowerCase(varName.charAt(0)) + varName.substring(1);
				if (plural) varName += "s";
				name = varName + "_" + nameCounts.compute(varName, (k, v) -> (v == null) ? 1 : v + 1);
			}

			mv.visitLocalVariable(
					name,
					descriptor,
					remapper.mapSignature(signature, true),
					start,
					end,
					index);
		}

		private static boolean isValidJavaIdentifier(String s) {
			if (s.isEmpty()) return false;

			int cp = s.codePointAt(0);
			if (!Character.isJavaIdentifierStart(cp)) return false;

			for (int i = Character.charCount(cp), max = s.length(); i < max; i += Character.charCount(cp)) {
				cp = s.codePointAt(i);
				if (!Character.isJavaIdentifierPart(cp)) return false;
			}

			return true;
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			if (mv == null) return;

			Handle implemented = getLambdaImplementedMethod(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);

			if (implemented != null) {
				name = remapper.mapMethodName(implemented.getOwner(), implemented.getName(), implemented.getDesc());
			} else {
				name = remapper.mapInvokeDynamicMethodName(name, descriptor);
			}

			for (int i = 0; i < bootstrapMethodArguments.length; i++) {
				bootstrapMethodArguments[i] = remapper.mapValue(bootstrapMethodArguments[i]);
			}

			mv.visitInvokeDynamicInsn(
					name,
					remapper.mapMethodDesc(descriptor), (Handle) remapper.mapValue(bootstrapMethodHandle),
					bootstrapMethodArguments);
		}

		private static Handle getLambdaImplementedMethod(String name, String desc, Handle bsm, Object... bsmArgs) {
			if (isJavaLambdaMetafactory(bsm)) {
				assert desc.endsWith(";");
				return new Handle(Opcodes.H_INVOKEINTERFACE, desc.substring(desc.lastIndexOf(')') + 2, desc.length() - 1), name, ((Type) bsmArgs[0]).getDescriptor(), true);
			} else {
				System.out.printf("unknown invokedynamic bsm: %s/%s%s (tag=%d iif=%b)%n", bsm.getOwner(), bsm.getName(), bsm.getDesc(), bsm.getTag(), bsm.isInterface());

				return null;
			}
		}

		private static boolean isJavaLambdaMetafactory(Handle bsm) {
			return bsm.getTag() == Opcodes.H_INVOKESTATIC
					&& bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
					&& (bsm.getName().equals("metafactory")
							&& bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
							|| bsm.getName().equals("altMetafactory")
							&& bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"))
					&& !bsm.isInterface();
		}
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return createAsnAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
	}

	public static AnnotationRemapper createAsnAnnotationRemapper(String desc, AnnotationVisitor annotationVisitor, Remapper remapper) {
		return annotationVisitor == null ? null : new AsmAnnotationRemapper(remapper.mapDesc(desc), annotationVisitor, remapper);
	}

	private static class AsmAnnotationRemapper extends AnnotationRemapper {
		private final String annotationClass;
		public AsmAnnotationRemapper(String baseDesc, AnnotationVisitor annotationVisitor, Remapper remapper) {
			super(annotationVisitor, remapper);
			annotationClass = Type.getType(baseDesc).getInternalName();
		}

		@Override
		public void visit(String name, Object value) {
			String desc = "()"+Type.getDescriptor(value.getClass());
			if(remapper instanceof AsmRemapper) {
				System.err.printf("remapping annotation %s -> %s\n", name, remapper.mapMethodName(annotationClass, name, desc));
				super.visit(remapper.mapMethodName(annotationClass, name, desc), value);
			} else {
				super.visit(name, value);
			}
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			return AsmClassRemapper.createAsnAnnotationRemapper(descriptor, super.visitAnnotation(name, descriptor), remapper);
		}
	}
}
