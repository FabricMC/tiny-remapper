/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, FabricMC
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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public abstract class VisitTrackingClassRemapper extends ClassRemapper {
	public VisitTrackingClassRemapper(ClassVisitor classVisitor, Remapper remapper) {
		super(classVisitor, remapper);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		onVisit(VisitKind.INITIAL);
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public void visitSource(String source, String debug) {
		onVisit(VisitKind.SOURCE);
		super.visitSource(source, debug);
	}

	@Override
	public ModuleVisitor visitModule(String name, int access, String version) {
		onVisit(VisitKind.MODULE);
		return super.visitModule(name, access, version);
	}

	@Override
	public void visitNestHost(String nestHost) {
		onVisit(VisitKind.NEST_HOST);
		super.visitNestHost(nestHost);
	}

	@Override
	public void visitPermittedSubclass(String permittedSubclass) {
		onVisit(VisitKind.PERMITTED_SUBCLASS);
		super.visitPermittedSubclass(permittedSubclass);
	}

	@Override
	public void visitOuterClass(String owner, String name, String descriptor) {
		onVisit(VisitKind.OUTER_CLASS);
		super.visitOuterClass(owner, name, descriptor);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		onVisit(VisitKind.ANNOTATION);
		return super.visitAnnotation(descriptor, visible);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		onVisit(VisitKind.TYPE_ANNOTATION);
		return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
	}

	@Override
	public void visitAttribute(Attribute attribute) {
		onVisit(VisitKind.ATTRIBUTE);
		super.visitAttribute(attribute);
	}

	@Override
	public void visitNestMember(String nestMember) {
		onVisit(VisitKind.NEST_MEMBER);
		super.visitNestMember(nestMember);
	}

	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		onVisit(VisitKind.INNER_CLASS);
		super.visitInnerClass(name, outerName, innerName, access);
	}

	@Override
	public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
		onVisit(VisitKind.RECORD_COMPONENT);
		return super.visitRecordComponent(name, descriptor, signature);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		onVisit(VisitKind.FIELD);
		return super.visitField(access, name, descriptor, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		onVisit(VisitKind.METHOD);
		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	public void visitEnd() {
		onVisit(VisitKind.END);
		super.visitEnd();
	}

	protected abstract void onVisit(VisitKind kind);

	protected enum VisitKind {
		// in visitation order
		INITIAL, SOURCE, MODULE, NEST_HOST, PERMITTED_SUBCLASS, OUTER_CLASS,
		ANNOTATION, TYPE_ANNOTATION, ATTRIBUTE,
		NEST_MEMBER, INNER_CLASS, RECORD_COMPONENT, FIELD, METHOD,
		END
	}
}
