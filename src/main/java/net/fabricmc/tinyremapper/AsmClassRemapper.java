/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2018, 2023, FabricMC
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.lang.model.SourceVersion;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.FieldRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.RecordComponentRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

import net.fabricmc.tinyremapper.api.TrMember;

final class AsmClassRemapper extends VisitTrackingClassRemapper {
	AsmClassRemapper(ClassVisitor cv, AsmRemapper remapper,
			boolean rebuildSourceFilenames, boolean checkPackageAccess, boolean skipLocalMapping,
			boolean renameInvalidLocals, Pattern invalidLvNamePattern, boolean inferNameFromSameLvIndex) {
		super(cv, remapper);
		this.rebuildSourceFilenames = rebuildSourceFilenames;
		this.checkPackageAccess = checkPackageAccess;
		this.skipLocalMapping = skipLocalMapping;
		this.renameInvalidLocals = renameInvalidLocals;
		this.invalidLvNamePattern = invalidLvNamePattern;
		this.inferNameFromSameLvIndex = inferNameFromSameLvIndex;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		if (checkPackageAccess) {
			AsmRemapper remapper = (AsmRemapper) this.remapper;

			if (superName != null) PackageAccessChecker.checkClass(name, superName, "super class", remapper);

			if (interfaces != null) {
				for (String iface : interfaces) {
					PackageAccessChecker.checkClass(name, iface, "super interface", remapper);
				}
			}
		}

		sourceNameVisited = false;

		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public void visitSource(String source, String debug) {
		sourceNameVisited = true;

		if (!rebuildSourceFilenames) {
			super.visitSource(source, debug);
			return;
		}

		String mappedClsName = remapper.map(className);
		// strip inner classes
		int end = mappedClsName.indexOf('$');
		if (end <= 0) end = mappedClsName.length(); // require at least 1 character for the outer class
		// strip package
		int start = mappedClsName.lastIndexOf('/', end - 1) + 1; // avoid searching after $ to support weird nested class names like a$b/c
		if (end <= start) end = mappedClsName.length();

		super.visitSource(mappedClsName.substring(start, end).concat(".java"), debug);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		if (checkPackageAccess) {
			PackageAccessChecker.checkDesc(className, descriptor, "field descriptor", (AsmRemapper) remapper);
		}

		return super.visitField(access, name, descriptor, signature, value);
	}

	@Override
	protected FieldVisitor createFieldRemapper(FieldVisitor fieldVisitor) {
		return new AsmFieldRemapper(fieldVisitor, (AsmRemapper) remapper);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (checkPackageAccess) {
			PackageAccessChecker.checkDesc(className, descriptor, "method descriptor", (AsmRemapper) remapper);
		}

		if (!skipLocalMapping || renameInvalidLocals) {
			methodNode = new MethodNode(api, access, name, descriptor, signature, exceptions);
		}

		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor methodVisitor) {
		return new AsmMethodRemapper(methodVisitor, (AsmRemapper) remapper, className, methodNode,
				checkPackageAccess, skipLocalMapping, renameInvalidLocals, invalidLvNamePattern, inferNameFromSameLvIndex);
	}

	@Override
	protected RecordComponentVisitor createRecordComponentRemapper(RecordComponentVisitor recordComponentVisitor) {
		return new AsmRecordComponentRemapper(recordComponentVisitor, (AsmRemapper) remapper);
	}

	@Override
	public AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotationVisitor) {
		return new AsmAnnotationRemapper(descriptor, annotationVisitor, (AsmRemapper) remapper);
	}

	@Override
	public void visitEnd() {
		((AsmRemapper) remapper).finish(className, cv);

		super.visitEnd();
	}

	@Override
	protected void onVisit(VisitKind kind) {
		if (rebuildSourceFilenames && !sourceNameVisited && kind.ordinal() > VisitKind.SOURCE.ordinal()) {
			visitSource(null, null);
		}
	}

	private final boolean rebuildSourceFilenames;
	private final boolean checkPackageAccess;
	private final boolean skipLocalMapping;
	private final boolean renameInvalidLocals;
	private final Pattern invalidLvNamePattern;
	private final boolean inferNameFromSameLvIndex;
	private boolean sourceNameVisited;
	private MethodNode methodNode;

	private static class AsmFieldRemapper extends FieldRemapper {
		AsmFieldRemapper(FieldVisitor fieldVisitor, AsmRemapper remapper) {
			super(fieldVisitor, remapper);
		}

		@Override
		public AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotationVisitor) {
			return new AsmAnnotationRemapper(descriptor, annotationVisitor, (AsmRemapper) remapper);
		}
	}

	private static class AsmMethodRemapper extends MethodRemapper {
		private final TinyRemapper tr;
		AsmMethodRemapper(MethodVisitor methodVisitor,
				AsmRemapper remapper,
				String owner,
				MethodNode methodNode,
				boolean checkPackageAccess,
				boolean skipLocalMapping,
				boolean renameInvalidLocals,
				Pattern invalidLvNamePattern,
				boolean inferNameFromSameLvIndex) {
			super(methodNode != null ? methodNode : methodVisitor, remapper);
			this.owner = owner;
			this.methodNode = methodNode;
			this.output = methodVisitor;
			this.checkPackageAccess = checkPackageAccess;
			this.skipLocalMapping = skipLocalMapping;
			this.renameInvalidLocals = renameInvalidLocals;
			this.invalidLvNamePattern = invalidLvNamePattern;
			this.inferNameFromSameLvIndex = inferNameFromSameLvIndex;
			this.tr = remapper.tr;
		}

		@Override
		public AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotationVisitor) {
			return new AsmAnnotationRemapper(descriptor, annotationVisitor, (AsmRemapper) remapper);
		}

		@Override
		public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
			if (checkPackageAccess) {
				PackageAccessChecker.checkClass(this.owner, type, "try-catch", (AsmRemapper) remapper);
			}

			super.visitTryCatchBlock(start, end, handler, type);
		}

		@Override
		public void visitTypeInsn(int opcode, String type) {
			if (checkPackageAccess) {
				PackageAccessChecker.checkClass(this.owner, type, "type instruction", (AsmRemapper) remapper);
			}

			super.visitTypeInsn(opcode, type);
		}

		@Override
		public void visitLdcInsn(Object value) {
			if (checkPackageAccess) {
				PackageAccessChecker.checkValue(this.owner, value, "ldc instruction", (AsmRemapper) remapper);
			}

			super.visitLdcInsn(value);
		}

		@Override
		public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
			if (checkPackageAccess) {
				PackageAccessChecker.checkDesc(this.owner, descriptor, "multianewarray instruction", (AsmRemapper) remapper);
			}

			super.visitMultiANewArrayInsn(descriptor, numDimensions);
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
			if (checkPackageAccess) {
				PackageAccessChecker.checkMember(this.owner, owner, name, descriptor, TrMember.MemberType.FIELD, "field instruction", (AsmRemapper) remapper);
			}

			super.visitFieldInsn(opcode, owner, name, descriptor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			if (checkPackageAccess) {
				PackageAccessChecker.checkMember(this.owner, owner, name, descriptor, TrMember.MemberType.METHOD, "method instruction", (AsmRemapper) remapper);
			}

			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			Handle implemented = getLambdaImplementedMethod(name, descriptor, bootstrapMethodHandle, tr.knownIndyBsm, bootstrapMethodArguments);

			if (implemented != null) {
				name = remapper.mapMethodName(implemented.getOwner(), implemented.getName(), implemented.getDesc());
			} else {
				name = remapper.mapInvokeDynamicMethodName(name, descriptor);
			}

			for (int i = 0; i < bootstrapMethodArguments.length; i++) {
				bootstrapMethodArguments[i] = remapper.mapValue(bootstrapMethodArguments[i]);
			}

			// bypass remapper
			mv.visitInvokeDynamicInsn(name,
					remapper.mapMethodDesc(descriptor), (Handle) remapper.mapValue(bootstrapMethodHandle),
					bootstrapMethodArguments);
		}

		private static Handle getLambdaImplementedMethod(String name, String desc, Handle bsm, Set<String> knownIndyBsm, Object... bsmArgs) {
			if (isJavaLambdaMetafactory(bsm)) {
				assert desc.endsWith(";");
				return new Handle(Opcodes.H_INVOKEINTERFACE, desc.substring(desc.lastIndexOf(')') + 2, desc.length() - 1), name, ((Type) bsmArgs[0]).getDescriptor(), true);
			} else if (knownIndyBsm.contains(bsm.getOwner())) {
				return null;
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
			if (methodNode != null) {
				if (!skipLocalMapping
						|| renameInvalidLocals && (methodNode.localVariables != null && !methodNode.localVariables.isEmpty() || methodNode.parameters != null && !methodNode.parameters.isEmpty())) {
					processLocals();
				}

				methodNode.visitEnd();
				methodNode.accept(output);
			} else {
				super.visitEnd();
			}
		}

		private void processLocals() {
			final boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
			final Type[] argTypes = Type.getArgumentTypes(methodNode.desc);
			final int argLvSize = getLvIndex(argTypes.length, isStatic, argTypes);
			final String[] args = new String[argTypes.length];

			// grab arg names from parameters
			if (methodNode.parameters != null && methodNode.parameters.size() == args.length) {
				for (int i = 0; i < args.length; i++) {
					args[i] = methodNode.parameters.get(i).name;
				}
			} else {
				assert methodNode.parameters == null;
			}

			// grab arg names from lvs, fix "this", remap vars
			if (methodNode.localVariables != null) {
				for (int i = 0; i < methodNode.localVariables.size(); i++) {
					LocalVariableNode lv = methodNode.localVariables.get(i);

					if (!isStatic && lv.index == 0) { // this ref
						lv.name = "this";
					} else if (lv.index < argLvSize) { // arg
						int asmIndex = getAsmIndex(lv.index, isStatic, argTypes);
						String existingName = args[asmIndex];

						// replace if missing or better; don't check for keywords, will be renamed later
						if (existingName == null || !isValidJavaIdentifier(existingName) && isValidJavaIdentifier(lv.name)) {
							args[asmIndex] = lv.name;
						}

						// remap+fix later
					} else { // var
						if (!skipLocalMapping) {
							int startOpIdx = 0;
							AbstractInsnNode start = lv.start;

							while ((start = start.getPrevious()) != null) {
								if (start.getOpcode() >= 0) startOpIdx++;
							}

							lv.name = ((AsmRemapper) remapper).mapMethodVar(owner, methodNode.name, methodNode.desc, lv.index, startOpIdx, i, lv.name);

							if (renameInvalidLocals && isValidLvName(lv.name)) { // block valid name from generation
								nameCounts.putIfAbsent(lv.name, 1);
							}
						}

						// fix later
					}
				}
			}

			// remap args
			if (!skipLocalMapping) {
				for (int i = 0; i < args.length; i++) {
					args[i] = ((AsmRemapper) remapper).mapMethodArg(owner, methodNode.name, methodNode.desc, getLvIndex(i, isStatic, argTypes), args[i]);

					if (renameInvalidLocals && isValidLvName(args[i])) { // block valid name from generation
						nameCounts.putIfAbsent(args[i], 1);
					}
				}
			}

			// fix args
			if (renameInvalidLocals) {
				for (int i = 0; i < args.length; i++) {
					if (!isValidLvName(args[i])) {
						args[i] = getNameFromType(remapper.mapDesc(argTypes[i].getDescriptor()), true);
					}
				}
			}

			boolean hasAnyArgs = false;
			boolean hasAllArgs = true;

			for (String arg : args) {
				if (arg != null) {
					hasAnyArgs = true;
				} else {
					hasAllArgs = false;
				}
			}

			// update lvs, fix vars
			if (methodNode.localVariables != null
					|| hasAnyArgs && (methodNode.access & Opcodes.ACC_ABSTRACT) == 0) { // no lvt without method body
				if (methodNode.localVariables == null) {
					methodNode.localVariables = new ArrayList<>();
				}

				boolean[] argsWritten = new boolean[args.length];

				lvLoop: for (int i = 0; i < methodNode.localVariables.size(); i++) {
					LocalVariableNode lv = methodNode.localVariables.get(i);

					if (!isStatic && lv.index == 0) { // this ref
						// nothing
					} else if (lv.index < argLvSize) { // arg
						int asmIndex = getAsmIndex(lv.index, isStatic, argTypes);
						lv.name = args[asmIndex];
						argsWritten[asmIndex] = true;
					} else { // var
						if (renameInvalidLocals && !isValidLvName(lv.name)) {
							if (inferNameFromSameLvIndex) {
								for (int j = 0; j < methodNode.localVariables.size(); j++) {
									if (j == i) continue;

									LocalVariableNode otherLv = methodNode.localVariables.get(j);

									if (otherLv.index == lv.index
											&& otherLv.name != null
											&& otherLv.desc.equals(lv.desc)
											&& (j < i || isValidLvName(otherLv.name))) {
										lv.name = otherLv.name;
										continue lvLoop;
									}
								}
							}

							lv.name = getNameFromType(lv.desc, false);
						}
					}
				}

				LabelNode start = null;
				LabelNode end = null;

				for (int i = 0; i < args.length; i++) {
					if (!argsWritten[i] && args[i] != null) {
						if (start == null) { // lazy initialize start + end by finding the first and last label node
							boolean pastStart = false; // whether any actual instructions were already encountered

							for (Iterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
								AbstractInsnNode ain = it.next();

								if (ain.getType() == AbstractInsnNode.LABEL) {
									LabelNode label = (LabelNode) ain;
									if (start == null && !pastStart) start = label; // start label must precede all instructions
									end = label;
								} else if (ain.getOpcode() >= 0) { // actual instruction
									pastStart = true;
									end = null; // end must be after all instructions
								}
							}

							if (start == null) { // no labels -> can't create lvs
								start = new LabelNode();
								methodNode.instructions.insert(start);
							}

							if (end == null) {
								if (!pastStart) {
									end = start;
								} else {
									end = new LabelNode();
									methodNode.instructions.add(end);
								}
							}
						}

						methodNode.localVariables.add(new LocalVariableNode(args[i], remapper.mapDesc(argTypes[i].getDescriptor()), null, start, end, getLvIndex(i, isStatic, argTypes)));
					}
				}
			}

			// update parameters
			if (methodNode.parameters != null
					|| hasAllArgs && args.length > 0 // avoid creating MethodParameters attribute with missing names since they trigger a Kotlin compiler bug
					|| hasAnyArgs && (methodNode.access & Opcodes.ACC_ABSTRACT) != 0) { // .. unless parameters are the only way to specify args (no method body)
				if (methodNode.parameters == null) {
					methodNode.parameters = new ArrayList<>(args.length);
				}

				while (methodNode.parameters.size() < args.length) {
					methodNode.parameters.add(new ParameterNode(null, 0));
				}

				for (int i = 0; i < args.length; i++) {
					methodNode.parameters.get(i).name = args[i];
				}
			}
		}

		private static int getLvIndex(int asmIndex, boolean isStatic, Type[] argTypes) {
			int ret = 0;

			if (!isStatic) ret++;

			for (int i = 0; i < asmIndex; i++) {
				ret += argTypes[i].getSize();
			}

			return ret;
		}

		private static int getAsmIndex(int lvIndex, boolean isStatic, Type[] argTypes) {
			if (!isStatic) lvIndex--;

			for (int i = 0; i < argTypes.length; i++) {
				if (lvIndex == 0) return i;
				lvIndex -= argTypes[i].getSize();
			}

			return -1;
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

				char first = type.charAt(start);
				char firstLc = Character.toLowerCase(first);

				if (first == firstLc) { // type is already lower case, the var name would shade the type
					varName = null;
				} else {
					varName = firstLc + type.substring(start + 1, type.length() - 1);
				}

				// Only check for invalid identifiers, keyword check is performed below
				if (!isValidJavaIdentifier(varName)) {
					varName = isArg ? "arg" : "lv"; // lv instead of var to avoid confusion with Java 10's var keyword
				}

				incrementLetter = false;
				break;
			}
			default:
				throw new IllegalStateException();
			}

			boolean hasPluralS = false;

			if (plural) {
				String pluralVarName = varName + 's';

				// Appending 's' could make name invalid, e.g. "clas" -> "class" (keyword)
				if (!isJavaKeyword(pluralVarName)) {
					varName = pluralVarName;
					hasPluralS = true;
				}
			}

			if (incrementLetter) {
				int index = -1;

				while (nameCounts.putIfAbsent(varName, 1) != null || isJavaKeyword(varName)) {
					if (index < 0) index = getNameIndex(varName, hasPluralS);

					varName = getIndexName(++index, plural);
				}

				return varName;
			} else {
				String baseVarName = varName;
				int count = nameCounts.compute(baseVarName, (k, v) -> (v == null) ? 1 : v + 1);

				if (count == 1) {
					if (isJavaKeyword(baseVarName)) {
						varName += '_';
					} else {
						return varName; // name does not exist yet, so can return fast here
					}
				} else {
					varName = baseVarName + Integer.toString(count);
				}

				/*
				 * Check if name is not taken yet, count only indicates where to continue
				 * numbering for baseVarName, but does not guarantee that there is no
				 * other variable which already has that name, e.g.:
				 * (MyClass ?, MyClass2 ?, MyClass ?) -> (MyClass myClass, MyClass2 myClass2, !myClass2 is already taken!)
				 */
				while (nameCounts.putIfAbsent(varName, 1) != null) {
					varName = baseVarName + Integer.toString(count++);
				}

				nameCounts.put(baseVarName, count); // update name count

				return varName;
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

		private boolean isValidLvName(String s) {
			return isValidJavaIdentifier(s) && !isJavaKeyword(s)
					&& (invalidLvNamePattern == null || !invalidLvNamePattern.matcher(s).matches());
		}

		private static boolean isValidJavaIdentifier(String s) {
			return s != null && !s.isEmpty() && SourceVersion.isIdentifier(s)
					// Ignorable characters cannot be represented in source code,
					// would be ignored when re-compiled
					&& !s.codePoints().anyMatch(Character::isIdentifierIgnorable);
		}

		private static boolean isJavaKeyword(String s) {
			// TODO: Use SourceVersion.isKeyword(CharSequence, SourceVersion) in Java 9
			//       to make it independent from JDK version
			return SourceVersion.isKeyword(s);
		}

		private static final String[] singleCharStrings = {
				"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
				"n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
		};

		private final String owner;
		private final MethodNode methodNode;
		private final MethodVisitor output;
		private final Map<String, Integer> nameCounts = new HashMap<>();
		private final boolean checkPackageAccess;
		private final boolean skipLocalMapping;
		private final boolean renameInvalidLocals;
		private final Pattern invalidLvNamePattern;
		private final boolean inferNameFromSameLvIndex;
	}

	private static class AsmRecordComponentRemapper extends RecordComponentRemapper {
		AsmRecordComponentRemapper(RecordComponentVisitor recordComponentVisitor, AsmRemapper remapper) {
			super(recordComponentVisitor, remapper);
		}

		@Override
		public AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotationVisitor) {
			return new AsmAnnotationRemapper(descriptor, annotationVisitor, (AsmRemapper) remapper);
		}
	}

	/**
	 * Since sfPlayer want to infer the method descriptor when possible, we need to implement all remapping logic by
	 * ourselves.
	 */
	private static class AsmAnnotationRemapper extends AnnotationVisitor {
		protected final String descriptor;
		protected final AsmRemapper remapper;

		AsmAnnotationRemapper(String descriptor, AnnotationVisitor annotationVisitor, AsmRemapper remapper) {
			super(Opcodes.ASM9, annotationVisitor);
			this.descriptor = descriptor;
			this.remapper = remapper;
		}

		@Override
		public void visit(String name, Object value) {
			super.visit(
					mapAnnotationAttributeName(name, getDescriptor(value)),
					remapper.mapValue(value));
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			super.visitEnum(
					mapAnnotationAttributeName(name, descriptor),
					remapper.mapDesc(descriptor),
					remapper.mapFieldName(Type.getType(descriptor).getInternalName(), value, descriptor));
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			AnnotationVisitor annotationVisitor = super.visitAnnotation(
					mapAnnotationAttributeName(name, descriptor),
					remapper.mapDesc(descriptor));

			if (annotationVisitor == null) {
				return null;
			} else {
				return annotationVisitor == av ? this : createAnnotationRemapper(descriptor, annotationVisitor);
			}
		}

		public AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotationVisitor) {
			return new AsmAnnotationRemapper(descriptor, annotationVisitor, remapper);
		}

		/**
		 * Some hacks to allow inferring from elements in the array. {@code super.visitArray} will be called inside
		 * {@link AsmArrayAttributeAnnotationRemapper}.
		 */
		@Override
		public AnnotationVisitor visitArray(String name) {
			return new AsmArrayAttributeAnnotationRemapper(name,
					(desc) -> super.visitArray(mapAnnotationAttributeName(name, desc == null ? null : "[" + desc)),
					remapper);
		}

		protected String mapAnnotationAttributeName(String name, String attributeDesc) {
			if (descriptor == null || name == null) {
				return name;
			}

			return remapper.mapAnnotationAttributeName(descriptor, name, attributeDesc);
		}

		protected static String getDescriptor(Object value) {
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

		private static class AsmArrayAttributeAnnotationRemapper extends AsmAnnotationRemapper {
			protected final String arrayName;
			protected final Function<String, AnnotationVisitor> avSupplier;

			AsmArrayAttributeAnnotationRemapper(String arrayName, Function<String, AnnotationVisitor> avSupplier, AsmRemapper remapper) {
				super(null, null, remapper);

				this.arrayName = arrayName;
				this.avSupplier = Objects.requireNonNull(avSupplier);
			}

			@Override
			public void visit(String name, Object value) {
				if (av == null) av = avSupplier.apply(getDescriptor(value));

				super.visit(name, value);
			}

			@Override
			public void visitEnum(String name, String descriptor, String value) {
				if (av == null) av = avSupplier.apply(descriptor);

				super.visitEnum(name, descriptor, value);
			}

			@Override
			public AnnotationVisitor visitAnnotation(String name, String descriptor) {
				if (av == null) av = avSupplier.apply(descriptor);

				return super.visitAnnotation(name, descriptor);
			}

			@Override
			public AnnotationVisitor visitArray(String name) {
				return new AsmArrayAttributeAnnotationRemapper(name,
						(desc) -> {
							if (this.av == null) this.av = this.avSupplier.apply(desc == null ? null : "[" + desc);

							return super.visitArray(mapAnnotationAttributeName(name, desc == null ? null : "[" + desc));
						},
						remapper);
			}

			@Override
			public void visitEnd() {
				if (av == null) av = avSupplier.apply(null);

				super.visitEnd();
			}
		}
	}
}
