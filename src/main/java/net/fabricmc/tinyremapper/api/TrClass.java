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

package net.fabricmc.tinyremapper.api;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public interface TrClass {
	TrEnvironment getEnvironment();

	String getName();
	String getSuperName();
	List<String> getInterfaceNames();
	String getSignature();
	int getAccess();

	TrClass getSuperClass();
	List<? extends TrClass> getInterfaces();

	Collection<? extends TrClass> getParents();
	Collection<? extends TrClass> getChildren();

	TrField getField(String name, String desc);
	TrMethod getMethod(String name, String desc);

	Collection<? extends TrField> getFields();
	Collection<? extends TrMethod> getMethods();
	Collection<? extends TrMember> getMembers();

	Collection<TrField> getFields(String name, String desc, boolean isDescPrefix, Predicate<TrField> filter, Collection<TrField> out);
	Collection<TrMethod> getMethods(String name, String desc, boolean isDescPrefix, Predicate<TrMethod> filter, Collection<TrMethod> out);

	TrField resolveField(String name, String desc);
	TrMethod resolveMethod(String name, String desc);

	/**
	 * Get fields in the class, including the one inherited from super-class or super-interfaces, satisfy the search query.
	 * @param name the name of the field. Nullable.
	 * @param desc the descriptor (or descriptor prefix) of the field. Nullable.
	 * @param isDescPrefix is {@code descPrefix} a full qualified descriptor or a prefix.
	 * @param filter any additional constraint. Nullable.
	 * @param out if not {@code null}, then reuse this collection instead of allocate a new one. The behaviour
	 *            is undefined is the collection is non-empty. Nullable.
	 * @return the query result.
	 */
	Collection<TrField> resolveFields(String name, String desc, boolean isDescPrefix, Predicate<TrField> filter, Collection<TrField> out);

	/**
	 * Get methods in the class, including the one inherited from super-class or super-interfaces, satisfy the search query.
	 * @param name the name of the method. Nullable.
	 * @param desc the descriptor (or descriptor prefix) of the method. Nullable.
	 * @param isDescPrefix is {@code descPrefix} a full qualified descriptor or a prefix.
	 * @param filter any additional constraint. Nullable.
	 * @param out if not {@code null}, then reuse this collection instead of the internal one. The behaviour
	 *            is undefined is the collection is non-empty. Nullable.
	 * @return the query result.
	 */
	Collection<TrMethod> resolveMethods(String name, String desc, boolean isDescPrefix, Predicate<TrMethod> filter, Collection<TrMethod> out);

	boolean isAssignableFrom(TrClass cls);
	void accept(ClassVisitor cv, int readerFlags);

	/**
	 * May be accessed from outside its package.
	 */
	default boolean isPublic() {
		return (getAccess() & Opcodes.ACC_PUBLIC) != 0;
	}

	/**
	 * No subclasses allowed.
	 */
	default boolean isFinal() {
		return (getAccess() & Opcodes.ACC_FINAL) != 0;
	}

	/**
	 * Is an interface, not a class.
	 */
	default boolean isInterface() {
		return (getAccess() & Opcodes.ACC_INTERFACE) != 0;
	}

	/**
	 * Declared {@code abstract}; must not be instantiated.
	 */
	default boolean isAbstract() {
		return (getAccess() & Opcodes.ACC_ABSTRACT) != 0;
	}

	/**
	 * Declared {@code synthetic}; not present in the source code.
	 */
	default boolean isSynthetic() {
		return (getAccess() & Opcodes.ACC_SYNTHETIC) != 0;
	}

	/**
	 * Declared as an annotation interface.
	 */
	default boolean isAnnotation() {
		return (getAccess() & Opcodes.ACC_ANNOTATION) != 0;
	}

	/**
	 * Declared as an enum class.
	 */
	default boolean isEnum() {
		return (getAccess() & Opcodes.ACC_ENUM) != 0;
	}

	/**
	 * Declare as a record class.
	 */
	default boolean isRecord() {
		return (getAccess() & Opcodes.ACC_RECORD) != 0;
	}

	/**
	 * Is a module, not a class or interface.
	 */
	default boolean isModule() {
		return (getAccess() & Opcodes.ACC_MODULE) != 0;
	}
}
