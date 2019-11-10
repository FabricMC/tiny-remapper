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
		// strip package
		int start = mappedClsName.lastIndexOf('/') + 1;
		// strip inner classes
		int end = mappedClsName.indexOf('$');
		if (end <= 0) end = mappedClsName.length(); // require at least 1 character for the outer class

		super.visitSource(mappedClsName.substring(start, end).concat(".java"), debug);
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
			this.argTypes = Type.getArgumentTypes(methodNode.desc);
			this.methodNode = methodNode;
			this.methodVisitor = methodVisitor;
			this.isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
			this.argLvSize = getLvIndex(argTypes.length);
			this.checkPackageAccess = checkPackageAccess;
			this.renameInvalidLocals = renameInvalidLocals;
			this.parameterNames = new String[argTypes.length];
			this.parameterAccess = new int[argTypes.length];
		}

		private int getLvIndex(int asmIndex) {
			int ret = 0;

			if (!isStatic) ret++;

			for (int i = 0; i < asmIndex; i++) {
				ret += argTypes[i].getSize();
			}

			return ret;
		}

		private int getAsmIndex(int lvIndex) {
			if (!isStatic) lvIndex--;

			for (int i = 0; i < argTypes.length; i++) {
				if (lvIndex == 0) return i;
				lvIndex -= argTypes[i].getSize();
			}

			return -1;
		}

		@Override
		public void visitParameter(String name, int access) {
			int asmIndex = parametersVisited++;
			name = ((AsmRemapper) remapper).mapMethodArg(owner, methodNode.name, methodNode.desc, getLvIndex(asmIndex), name);

			if (renameInvalidLocals && !isValidJavaIdentifier(name)) {
				name = getNameFromType(remapper.mapDesc(argTypes[asmIndex].getDescriptor()), true);
			}

			parameterNames[asmIndex] = name;
			parameterAccess[asmIndex] = access;
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
		public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int lvIndex) {
			if (methodNode == null) return;

			descriptor = remapper.mapDesc(descriptor);

			boolean isArg = lvIndex < argLvSize;
			int asmIndex;
			boolean assumeValid = false;

			if (lvIndex == 0 && !isStatic) {
				name = "this";
				assumeValid = true;
				asmIndex = -1;
			} else if (isArg) {
				asmIndex = getAsmIndex(lvIndex);

				if (parameterNames[asmIndex] != null) {
					/* at this point there are 4 scenarios
					 * 1. the parameter name was correct or mapped, the lv name isn't
					 * 2. the parameter name was correct or mapped, the lv name is as well
					 * 3. the parameter name was incorrect, unmapped and generated, the lv name is correct
					 * 4. the parameter name was incorrect, unmapped and generated, the lv name is incorrect
					 * Case 3 isn't being handled here since it's unlikely that the parameter name is present and wrong,
					 * while the lv name is also present and correct. It could be fixed by remembering whether the
					 * parameter name was generated and ignoring it here if that's the case.
					 */
					name = parameterNames[asmIndex];
					assumeValid = true;
				} else {
					name = ((AsmRemapper) remapper).mapMethodArg(owner, methodNode.name, methodNode.desc, lvIndex, name);
				}
			} else {
				asmIndex = -1;
			}

			if (!assumeValid && renameInvalidLocals && !isValidJavaIdentifier(name)) {
				name = getNameFromType(descriptor, isArg);
			}

			if (isArg && asmIndex >= 0) {
				parameterNames[asmIndex] = name;
			}

			methodNode.visitLocalVariable(
					name,
					descriptor,
					remapper.mapSignature(signature, true),
					start,
					end,
					lvIndex);
		}

		private String getNameFromType(String type, boolean isArg) {
			boolean plural = false;

			if (type.charAt(0) == '[') {
				plural = true;
				type = type.substring(type.lastIndexOf('[') + 1);
			}

			boolean incrementLetter = true;
			String varName;

			switch (type.charAt(0)) {
			case 'B': varName = "b"; break;
			case 'C': varName = "c"; break;
			case 'D': varName = "d"; break;
			case 'F': varName = "f"; break;
			case 'I': varName = "i"; break;
			case 'J': varName = "l"; break;
			case 'S': varName = "s"; break;
			case 'Z':
				varName = "bl";
				incrementLetter = false;
				break;
			case 'L': {
				// strip preceding packages and outer classes

				int start = type.lastIndexOf('/') + 1;
				int startDollar = type.lastIndexOf('$') + 1;

				if (startDollar > start && startDollar < type.length() - 1) {
					start = startDollar;
				} else if (start == 0) {
					start = 1;
				}

				// assemble, lowercase first char, apply plural s

				varName = Character.toLowerCase(type.charAt(start)) + type.substring(start + 1, type.length() - 1);

				if (!isValidJavaIdentifier(varName)) {
					varName = isArg ? "arg" : "var";
				}

				incrementLetter = false;
				break;
			}
			default:
				throw new IllegalStateException();
			}

			if (plural) varName += "s";

			if (incrementLetter) {
				int index = -1;

				while (nameCounts.putIfAbsent(varName, 1) != null) {
					if (index < 0) index = getNameIndex(varName, plural);

					varName = getIndexName(++index, plural);
				}

				return varName;
			} else {
				int count = nameCounts.compute(varName, (k, v) -> (v == null) ? 1 : v + 1);

				return count == 1 ? varName : varName.concat(Integer.toString(count));
			}
		}

		private static int getNameIndex(String name, boolean plural) {
			int ret = 0;

			for (int i = 0, max = name.length() - (plural ? 1 : 0); i < max; i++) {
				ret = ret * 26 + name.charAt(i) - 'a' + 1;
			}

			return ret - 1;
		}

		private static String getIndexName(int index, boolean plural) {
			if (index < 26 && !plural) {
				return singleCharStrings[index];
			} else {
				StringBuilder ret = new StringBuilder(2);

				do {
					int next = index / 26;
					int cur = index - next * 26;
					ret.append((char) ('a' + cur));
					index = next - 1;
				} while (index >= 0);

				ret.reverse();

				if (plural) ret.append('s');

				return ret.toString();
			}
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
			for (int i = 0; i < parameterNames.length; i++) {
				String name = parameterNames[i];

				if (name == null) {
					name = ((AsmRemapper) remapper).mapMethodArg(owner, methodNode.name, methodNode.desc, getLvIndex(i), name);

					if (renameInvalidLocals && !isValidJavaIdentifier(name)) {
						name = getNameFromType(remapper.mapDesc(argTypes[i].getDescriptor()), true);
					}
				}

				methodNode.visitParameter(name, parameterAccess[i]);
			}

			methodNode.visitEnd();
			methodNode.accept(methodVisitor);

			super.visitEnd();
		}

		private static final String[] singleCharStrings = {
				"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
				"n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
		};

		private final String owner;
		private final Type[] argTypes;
		private final MethodNode methodNode;
		private final MethodVisitor methodVisitor;
		private final boolean isStatic;
		private final int argLvSize;
		private final Map<String, Integer> nameCounts = new HashMap<>();
		private final boolean checkPackageAccess;
		private final boolean renameInvalidLocals;
		private final String[] parameterNames;
		private final int[] parameterAccess;
		private int parametersVisited;
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
