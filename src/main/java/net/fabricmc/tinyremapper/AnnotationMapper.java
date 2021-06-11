package net.fabricmc.tinyremapper;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.api.ClassHeader;
import net.fabricmc.tinyremapper.api.FieldHeader;
import net.fabricmc.tinyremapper.api.MethodHeader;

public interface AnnotationMapper {
	default AnnotationVisitor wrapClass(ClassHeader header, AnnotationVisitor writer, String desc, boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapClassTypeAnnotation(ClassHeader header, AnnotationVisitor writer, int typeRef, TypePath typePath, String desc, boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapField(FieldHeader header, AnnotationVisitor writer,
			String annotationDesc,
			boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapFieldTypeAnnotation(FieldHeader header, AnnotationVisitor writer,
			int typeRef,
			TypePath typePath,
			String annotationDesc,
			boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapMethod(MethodHeader header, AnnotationVisitor writer,
			String annotationDesc,
			boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapMethodTypeAnnotation(MethodHeader header, AnnotationVisitor writer,
			int typeRef,
			TypePath typePath,
			String annotationDesc,
			boolean visible) {
		return writer;
	}

	default AnnotationVisitor wrapMethodParameter(MethodHeader header, AnnotationVisitor writer,
			int parameter,
			String annotationDesc,
			boolean visible) {
		return writer;
	}
}
