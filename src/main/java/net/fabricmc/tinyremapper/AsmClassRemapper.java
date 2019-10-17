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

import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.tinyremapper.MemberInstance.MemberType;

import org.objectweb.asm.AnnotationVisitor;
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
	public AsmClassRemapper(ClassVisitor cv, AsmRemapper remapper, boolean checkPackageAccess, boolean renameInvalidLocals) {
		super(cv, remapper);

		this.checkPackageAccess = checkPackageAccess;
		this.renameInvalidLocals = renameInvalidLocals;
	}

	@Override
	public void visitSource(String source, String debug) {
		String mappedClsName = remapper.map(className);

		super.visitSource(mappedClsName.substring(mappedClsName.lastIndexOf('/') + 1).concat(".java"), debug);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		methodNode = new MethodNode(api, access, name, descriptor, signature, exceptions);

		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	protected FieldVisitor createFieldRemapper(FieldVisitor fieldVisitor) {
		return new AsmFieldRemapper(fieldVisitor, remapper);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor mv) {
		return new AsmMethodRemapper(mv, remapper, className, methodNode, checkPackageAccess, renameInvalidLocals);
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

	private final boolean checkPackageAccess;
	private final boolean renameInvalidLocals;
	private MethodNode methodNode;

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
		public AsmMethodRemapper(MethodVisitor methodVisitor, Remapper remapper, String owner, MethodNode methodNode, boolean checkPackageAccess, boolean renameInvalidLocals) {
			super(methodNode, remapper);

			this.owner = owner;
			this.methodNode = methodNode;
			this.methodVisitor = methodVisitor;
			this.isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
			this.argLvSize = getLvIndex(methodNode.desc, isStatic, Integer.MAX_VALUE);
			this.checkPackageAccess = checkPackageAccess;
			this.renameInvalidLocals = renameInvalidLocals;
			this.parameterNames = new String[argLvSize];
			this.parameterAccess = new int[argLvSize];
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
			final int lvIndex = getLvIndex(methodNode.desc, isStatic, argsVisited++);
			parameterNames[lvIndex] = name;
			parameterAccess[lvIndex] = access;
		}

		@Override
		public AnnotationVisitor visitAnnotationDefault() {
			return AsmClassRemapper.createAsmAnnotationRemapper(Type.getObjectType(owner).getDescriptor(), super.visitAnnotationDefault(), remapper);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), remapper);
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitParameterAnnotation(parameter, descriptor, visible), remapper);
		}

		@Override
		public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
			if (methodNode == null) return;

			descriptor = remapper.mapDesc(descriptor);

			if (index < argLvSize) {
				if(isValidJavaIdentifier(parameterNames[index])) { //look for valid parameter name
					name = parameterNames[index];
				}

				name = ((AsmRemapper) remapper).mapMethodArg(owner, methodNode.name, methodNode.desc, index, name);
			}

			if (renameInvalidLocals && !isValidJavaIdentifier(name)) {
				Type type = Type.getType(descriptor);
				name = getNameFromType(type);
			}

			if (index < argLvSize) {
				parameterNames[index] = name;
			}

			methodNode.visitLocalVariable(
					name,
					descriptor,
					remapper.mapSignature(signature, true),
					start,
					end,
					index);
		}

		private String getNameFromType(Type type) {
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
		public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
			if (checkPackageAccess) {
				((AsmRemapper) remapper).checkPackageAccess(this.owner, owner, name, descriptor, MemberType.FIELD);
			}

			super.visitFieldInsn(opcode, owner, name, descriptor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			if (checkPackageAccess) {
				((AsmRemapper) remapper).checkPackageAccess(this.owner, owner, name, descriptor, MemberType.METHOD);
			}

			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			if (methodNode == null) return;

			Handle implemented = getLambdaImplementedMethod(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);

			if (implemented != null) {
				name = remapper.mapMethodName(implemented.getOwner(), implemented.getName(), implemented.getDesc());
			} else {
				name = remapper.mapInvokeDynamicMethodName(name, descriptor);
			}

			for (int i = 0; i < bootstrapMethodArguments.length; i++) {
				bootstrapMethodArguments[i] = remapper.mapValue(bootstrapMethodArguments[i]);
			}

			methodNode.visitInvokeDynamicInsn(
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
			final Type[] paramTypes = Type.getArgumentTypes(methodNode.desc);

			for (int i = 0; i < paramTypes.length; i++) {
				final int lvIndex = getLvIndex(methodNode.desc, isStatic, i);

				//If LVT is not present, for example in an abstract method, the name will not be set in the array.
				//So simply map the method arg with the parameter name form LVT set as default and remap if invalid
				String name = ((AsmRemapper) remapper).mapMethodArg(owner, methodNode.name, methodNode.desc, lvIndex, parameterNames[lvIndex]);

				if(renameInvalidLocals && !isValidJavaIdentifier(name)) {
					name = getNameFromType(paramTypes[i]);
				}

				methodNode.visitParameter(name, parameterAccess[i]);
			}

			methodNode.visitEnd();
			methodNode.accept(methodVisitor);

			super.visitEnd();
		}

		private final String owner;
		private final MethodNode methodNode;
		private final MethodVisitor methodVisitor;
		private final boolean isStatic;
		private final int argLvSize;
		private final Map<String, Integer> nameCounts = new HashMap<>();
		private final boolean checkPackageAccess;
		private final boolean renameInvalidLocals;
		private final String[] parameterNames;
		private final int[] parameterAccess;
		private int argsVisited;
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
