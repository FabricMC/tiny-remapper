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
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.AnnotationRemapper;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.FieldRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;

class AsmClassRemapper extends ClassRemapper {
	public AsmClassRemapper(ClassVisitor cv, AsmRemapper remapper, boolean renameInvalidLocals) {
		super(cv, remapper);
		this.renameInvalidLocals = renameInvalidLocals;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		this.methodAccess = access;
		this.methodName = name;
		this.methodDesc = descriptor;

		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	protected FieldVisitor createFieldRemapper(FieldVisitor fieldVisitor) {
		return new AsmFieldRemapper(fieldVisitor, remapper);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor mv) {
		return new AsmMethodRemapper(mv, remapper, className, methodName, methodDesc, methodAccess, renameInvalidLocals);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return createAsmAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		return createAsmAnnotationRemapper(descriptor, super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), remapper);
	}

	public static AnnotationRemapper createAsmAnnotationRemapper(String desc, AnnotationVisitor annotationVisitor, Remapper remapper) {
		return annotationVisitor == null ? null : new AsmAnnotationRemapper(annotationVisitor, remapper, desc);
	}

	private final boolean renameInvalidLocals;
	private int methodAccess;
	private String methodName;
	private String methodDesc;

	private static class AsmFieldRemapper extends FieldRemapper {
		public AsmFieldRemapper(FieldVisitor fieldVisitor, Remapper remapper) {
			super(fieldVisitor, remapper);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), remapper);
		}
	}

	private static class AsmMethodRemapper extends MethodRemapper {
		public AsmMethodRemapper(MethodVisitor methodVisitor, Remapper remapper, String className, String name, String desc, int access, boolean renameInvalidLocals) {
			super(methodVisitor, remapper);

			this.owner = className;
			this.name = name;
			this.desc = desc;
			this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
			this.isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
			this.argLvSize = getLvIndex(desc, isStatic, Integer.MAX_VALUE);
			this.renameInvalidLocals = renameInvalidLocals;
		}

		private static int getLvIndex(String desc, boolean isStatic, int asmIndex) {
			int ret = 0;

			if (!isStatic) ret++;

			if (!desc.startsWith("()") && asmIndex > 0) {
				Type[] args = Type.getArgumentTypes(desc);

				for (int i = 0, max = Math.min(args.length, asmIndex); i < max; i++) {
					ret += args[i].getSize();
				}
			}

			return ret;
		}

		@Override
		public void visitParameter(String name, int access) {
			name = ((AsmRemapper) remapper).mapMethodArg(this.owner, this.name, this.desc, getLvIndex(this.desc, isStatic, argsVisited), name);

			if (renameInvalidLocals && !isValidJavaIdentifier(name)) {
				if (this.isAbstract) {
					Type type = Type.getArgumentTypes(this.desc)[argsVisited];
					name = getGeneratedName(type);
				} else {
					name = renamedInvalidLocals.getOrDefault(argsVisited, name);
				}
			}

			if(!renameInvalidLocals || isValidJavaIdentifier(name)) {
				argsVisited++;
				super.visitParameter(name, access);
			}
		}

		private void checkParameters(boolean forceCheck) {
			if ((checkedParams || this.desc.startsWith("()")) && !forceCheck) return;
			checkedParams = true;

			/* visitParameter() hasn't been called. checkParameters() gets called for every visit method that may
			 * follow visitParameters(), but never earlier, in accordance with the visit order constraints specified in
			 * MethodVistor.
			 * The first call to checkParameters() also happens before any super.visit* that must follow
			 * visitParameter(). The preceding check ensures in combination with the visitParameter implementation
			 * ensures being in this first call.
			 * This means it's now safe to recreate the MethodParameters attribute with dummy values by calling
			 * visitParameter() directly and letting its implementation fill any known arg names in. */

			int argCount = Type.getArgumentTypes(this.desc).length;

			for (int i = argsVisited; i < argCount && argsVisited == i; i++) {
				visitParameter(null, 0);
			}
		}

		@Override
		public AnnotationVisitor visitAnnotationDefault() {
			checkParameters(false);
			return AsmClassRemapper.createAsmAnnotationRemapper(Type.getObjectType(owner).getDescriptor(), super.visitAnnotationDefault(), remapper);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			checkParameters(false);
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			checkParameters(false);
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), remapper);
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
			checkParameters(false);
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitParameterAnnotation(parameter, descriptor, visible), remapper);
		}

		@Override
		public void visitAttribute(Attribute attribute) {
			checkParameters(false);
			super.visitAttribute(attribute);
		}

		@Override
		public void visitCode() {
			checkParameters(false);
			super.visitCode();
		}

		@Override
		public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
			if (mv == null) return;

			descriptor = remapper.mapDesc(descriptor);

			if (index < argLvSize) {
				name = ((AsmRemapper) remapper).mapMethodArg(this.owner, this.name, this.desc, index, name);
			}

			if (renameInvalidLocals && !isValidJavaIdentifier(name)) {
				Type type = Type.getType(descriptor);
				name = getGeneratedName(type);

				//make sure parameter name matches LVT
				if (index < argLvSize) {
					renamedInvalidLocals.put(index, name);
					checkParameters(true);
				}
			}

			mv.visitLocalVariable(
					name,
					descriptor,
					remapper.mapSignature(signature, true),
					start,
					end,
					index);
		}

		private String getGeneratedName(Type type) {
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
			return varName + "_" + nameCounts.compute(varName, (k, v) -> (v == null) ? 1 : v + 1);
		}

		private static boolean isValidJavaIdentifier(String s) {
			if (s == null || s.isEmpty()) return false;

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

		@Override
		public void visitEnd() {
			checkParameters(false);
			super.visitEnd();
		}

		private final String owner;
		private final String name;
		private final String desc;
		private final boolean isStatic;
		private final boolean isAbstract;
		private final int argLvSize;
		private final Map<String, Integer> nameCounts = new HashMap<>();
		private final boolean renameInvalidLocals;
		private final Map<Integer, String> renamedInvalidLocals = new HashMap<>();
		private int argsVisited;
		private boolean checkedParams = false;
	}

	private static class AsmAnnotationRemapper extends AnnotationRemapper {
		public AsmAnnotationRemapper(AnnotationVisitor annotationVisitor, Remapper remapper, String annotationDesc) {
			super(annotationVisitor, remapper);

			annotationClass = Type.getType(annotationDesc).getInternalName();
		}

		@Override
		public void visit(String name, Object value) {
			super.visit(mapAnnotationName(name, getDesc(value)), value);
		}

		private static String getDesc(Object value) {
			if (value instanceof Type) return ((Type) value).getDescriptor();

			Class<?> cls = value.getClass();

			if (Byte.class.isAssignableFrom(cls)) return "B";
			if (Boolean.class.isAssignableFrom(cls)) return "Z";
			if (Character.class.isAssignableFrom(cls)) return "C";
			if (Short.class.isAssignableFrom(cls)) return "S";
			if (Integer.class.isAssignableFrom(cls)) return "I";
			if (Long.class.isAssignableFrom(cls)) return "J";
			if (Float.class.isAssignableFrom(cls)) return "F";
			if (Double.class.isAssignableFrom(cls)) return "D";

			return Type.getDescriptor(cls);
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			super.visitEnum(mapAnnotationName(name, descriptor),
					descriptor,
					remapper.mapFieldName(Type.getType(descriptor).getInternalName(), value, descriptor));
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			return createNested(descriptor, av.visitAnnotation(mapAnnotationName(name, descriptor), descriptor));
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			// try to infer the descriptor from an element

			return new AnnotationVisitor(Opcodes.ASM7) {
				@Override
				public void visit(String name, Object value) {
					if (av == null) start(getDesc(value));

					super.visit(name, value);
				}

				@Override
				public void visitEnum(String name, String descriptor, String value) {
					if (av == null) start(descriptor);

					super.visitEnum(name, descriptor, value);
				}

				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (av == null) start(descriptor);

					return super.visitAnnotation(name, descriptor);
				}

				@Override
				public AnnotationVisitor visitArray(String name) {
					throw new IllegalStateException("nested arrays are disallowed by the jvm spec");
				}

				@Override
				public void visitEnd() {
					if (av == null) {
						// no element to infer from, try to find a mapping with a suitable owner+name+desc
						// there's no need to wrap the visitor in AsmAnnotationRemapper without any content to process

						String newName;

						if (name == null) { // used for default annotation values
							newName = null;
						} else {
							newName = ((AsmRemapper) remapper).mapMethodNamePrefixDesc(annotationClass, name, "()[");
						}

						av = AsmAnnotationRemapper.this.av.visitArray(newName);
					}

					super.visitEnd();
				}

				private void start(String desc) {
					assert av == null;

					desc = "["+desc;

					av = createNested(desc, AsmAnnotationRemapper.this.av.visitArray(mapAnnotationName(name, desc)));
				}
			};
		}

		private String mapAnnotationName(String name, String descriptor) {
			if (name == null) return null; // used for default annotation values

			return remapper.mapMethodName(annotationClass, name, "()"+descriptor);
		}

		private AnnotationVisitor createNested(String descriptor, AnnotationVisitor parent) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, parent, remapper);
		}

		private final String annotationClass;
	}
}
