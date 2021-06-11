package net.fabricmc.tinyremapper.api;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

public interface AnnotationMapper extends Opcodes {
	default AnnotationVisitor wrapClass(ClassHeader header, AnnotationVisitor writer, String desc, boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapClassTypeAnnotation(ClassHeader header, AnnotationVisitor writer, int typeRef, TypePath typePath, String desc, boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapField(MemberHeader header, AnnotationVisitor writer,
			String annotationDesc,
			boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapFieldTypeAnnotation(MemberHeader header, AnnotationVisitor writer,
			int typeRef,
			TypePath typePath,
			String annotationDesc,
			boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapMethod(MemberHeader header, AnnotationVisitor writer,
			String annotationDesc,
			boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapMethodTypeAnnotation(MemberHeader header, AnnotationVisitor writer,
			int typeRef,
			TypePath typePath,
			String annotationDesc,
			boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapMethodParameter(MemberHeader header, AnnotationVisitor writer,
			int parameter,
			String annotationDesc,
			boolean visible) {
		return writer;
	}
}
