package net.fabricmc.tinyremapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

public class MultiMethodVisitor extends MethodVisitor {
	// find   : super.(.*)
	// replace: for(MethodVisitor visitor : this.visitors) {\n\t\t\tvisitor.$1\n\t\t}

	private final Collection<MethodVisitor> visitors;
	private final int api;

	public MultiMethodVisitor(int api, Collection<MethodVisitor> visitors) {
		super(api);
		this.api = api;
		this.visitors = visitors;
	}

	@Override
	public void visitParameter(String name, int access) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitParameter(name, access);
		}
	}

	public static class MultiAnnotationVisitor extends AnnotationVisitor {
		private final List<AnnotationVisitor> annotations;
		private final int api;
		public <T> MultiAnnotationVisitor(int api, Collection<T> input, Function<T, AnnotationVisitor> function) {
			super(api);
			this.api = api;
			this.annotations = new ArrayList<>(input.size());
			for(T visitor : input) {
				this.annotations.add(function.apply(visitor));
			}
		}

		@Override
		public void visit(String name, Object value) {
			for(AnnotationVisitor annotation : this.annotations) {
				annotation.visit(name, value);
			}
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			for(AnnotationVisitor annotation : this.annotations) {
				annotation.visitEnum(name, descriptor, value);
			}
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			return new MultiAnnotationVisitor(this.api, this.annotations, a -> a.visitAnnotation(name, descriptor));
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			return new MultiAnnotationVisitor(this.api, this.annotations, a -> a.visitArray(name));
		}

		@Override
		public void visitEnd() {
			for(AnnotationVisitor annotation : this.annotations) {
				annotation.visitEnd();
			}
		}
	}

	@Override
	public AnnotationVisitor visitAnnotationDefault() {
		return new MultiAnnotationVisitor(this.api, this.visitors, MethodVisitor::visitAnnotationDefault);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return new MultiAnnotationVisitor(this.api, this.visitors, v -> v.visitAnnotation(descriptor, visible));
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		return new MultiAnnotationVisitor(this.api, this.visitors, v -> v.visitTypeAnnotation(typeRef, typePath, descriptor, visible));
	}

	@Override
	public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
		return new MultiAnnotationVisitor(this.api, visitors, v -> v.visitParameterAnnotation(parameter, descriptor, visible));
	}

	@Override
	public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		return new MultiAnnotationVisitor(this.api, this.visitors, v -> v.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible));
	}

	@Override
	public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,
			TypePath typePath,
			Label[] start,
			Label[] end,
			int[] index,
			String descriptor,
			boolean visible) {
		return new MultiAnnotationVisitor(this.api, this.visitors, v -> v.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible));
	}

	@Override
	public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		return new MultiAnnotationVisitor(this.api, this.visitors, v -> v.visitInsnAnnotation(typeRef, typePath, descriptor, visible));
	}

	@Override
	public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitAnnotableParameterCount(parameterCount, visible);
		}
	}


	@Override
	public void visitAttribute(Attribute attribute) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitAttribute(attribute);
		}
	}

	@Override
	public void visitCode() {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitCode();
		}
	}

	@Override
	public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitFrame(type, numLocal, local, numStack, stack);
		}
	}

	@Override
	public void visitInsn(int opcode) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitInsn(opcode);
		}
	}

	@Override
	public void visitIntInsn(int opcode, int operand) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitIntInsn(opcode, operand);
		}
	}

	@Override
	public void visitVarInsn(int opcode, int var) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitVarInsn(opcode, var);
		}
	}

	@Override
	public void visitTypeInsn(int opcode, String type) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitTypeInsn(opcode, type);
		}
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitFieldInsn(opcode, owner, name, descriptor);
		}
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitMethodInsn(opcode, owner, name, descriptor);
		}
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}
	}

	@Override
	public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
		}
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitJumpInsn(opcode, label);
		}
	}

	@Override
	public void visitLabel(Label label) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitLabel(label);
		}
	}

	@Override
	public void visitLdcInsn(Object value) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitLdcInsn(value);
		}
	}

	@Override
	public void visitIincInsn(int var, int increment) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitIincInsn(var, increment);
		}
	}

	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitTableSwitchInsn(min, max, dflt, labels);
		}
	}

	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitLookupSwitchInsn(dflt, keys, labels);
		}
	}

	@Override
	public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitMultiANewArrayInsn(descriptor, numDimensions);
		}
	}

	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitTryCatchBlock(start, end, handler, type);
		}
	}

	@Override
	public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitLocalVariable(name, descriptor, signature, start, end, index);
		}
	}


	@Override
	public void visitLineNumber(int line, Label start) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitLineNumber(line, start);
		}
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitMaxs(maxStack, maxLocals);
		}
	}

	@Override
	public void visitEnd() {
		for(MethodVisitor visitor : this.visitors) {
			visitor.visitEnd();
		}
	}
}
