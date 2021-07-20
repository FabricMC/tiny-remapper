package net.fabricmc.tinyremapper.api;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.objectweb.asm.Opcodes;

public interface TrClass {
	/**
	 * Get the {@code TrEnvironment} that this class belongs to.
	 */
	TrEnvironment getEnvironment();

	/**
	 * Get access opcodes.
	 */
	int getAccess();

	/**
	 * Get the internal of the class.
	 */
	String getName();

	/**
	 * Get the internal name of the super class.
	 */
	String getSuperName();

	/**
	 * Get the signature of the class.
	 */
	String getSignature();

	/**
	 * Get all direct interfaces of the class.
	 */
	List<String> getInterfaces();

	/**
	 * Get all interface of the class, both direct and indirect.
	 * <p>This guarantees a partial order such that the most specific interface
	 * will appear earlier in the list</p>
	 */
	List<String> resolveInterfaces();

	/**
	 * Get methods defined in the current class satisfy the search query.
	 * @param name the name of the method. Nullable.
	 * @param descPrefix the descriptor (or descriptor prefix) of the method. Nullable.
	 * @param isDescPrefix is {@code descPrefix} a full qualified descriptor or a prefix.
	 * @param filter any additional constraint. NotNull.
	 * @param collection if not {@code null}, then reuse this collection instead of the internal one. The behaviour
	 *                   is undefined is the collection is non-empty. Nullable.
	 * @return the query result.
	 */
	Collection<TrMember> getMethods(String name, String descPrefix, boolean isDescPrefix, Predicate<TrMember> filter, Collection<TrMember> collection);

	/**
	 * @see TrClass#getMethods(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> getMethods(String name, String descPrefix, boolean isDescPrefix, Collection<TrMember> collection) {
		return getMethods(name, descPrefix, isDescPrefix, m -> true, collection);
	}

	/**
	 * @see TrClass#getMethods(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> getMethods(String name, String desc, Collection<TrMember> collection) {
		return getMethods(name, desc, false, m -> true, collection);
	}

	/**
	 * @see TrClass#getMethods(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> getMethods(String name, Collection<TrMember> collection) {
		return getMethods(name, null, false, m -> true, collection);
	}

	/**
	 * @see TrClass#getMethods(String, String, boolean, Predicate, Collection)
	 */
	default TrMember getMethod(String name, String desc) {
		Collection<TrMember> member = getMethods(name, desc, null);

		if (member.size() == 1) {
			return member.stream().findAny().get();
		} else {
			return null;
		}
	}

	/**
	 * Get fields defined in the current class satisfy the search query.
	 * @param name the name of the field. Nullable.
	 * @param descPrefix the descriptor (or descriptor prefix) of the field. Nullable.
	 * @param isDescPrefix is {@code descPrefix} a full qualified descriptor or a prefix.
	 * @param filter any additional constraint. NotNull.
	 * @param collection if not {@code null}, then reuse this collection instead of allocate a new one. The behaviour
	 *                   is undefined is the collection is non-empty. Nullable.
	 * @return the query result.
	 */
	Collection<TrMember> getFields(String name, String descPrefix, boolean isDescPrefix, Predicate<TrMember> filter, Collection<TrMember> collection);

	/**
	 * @see TrClass#getFields(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> getFields(String name, String descPrefix, boolean isDescPrefix, Collection<TrMember> collection) {
		return getFields(name, descPrefix, isDescPrefix, m -> true, collection);
	}

	/**
	 * @see TrClass#getFields(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> getFields(String name, String desc, Collection<TrMember> collection) {
		return getFields(name, desc, false, m -> true, collection);
	}

	/**
	 * @see TrClass#getFields(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> getFields(String name, Collection<TrMember> collection) {
		return getFields(name, null, false, m -> true, collection);
	}

	/**
	 * @see TrClass#getFields(String, String, boolean, Predicate, Collection)
	 */
	default TrMember getField(String name, String desc) {
		Collection<TrMember> member = getFields(name, desc, null);

		if (member.size() == 1) {
			return member.stream().findAny().get();
		} else {
			return null;
		}
	}

	/**
	 * Get methods in the class, including the one inherited from super-class or super-interfaces, satisfy the search query.
	 * @param name the name of the method. Nullable.
	 * @param descPrefix the descriptor (or descriptor prefix) of the method. Nullable.
	 * @param isDescPrefix is {@code descPrefix} a full qualified descriptor or a prefix.
	 * @param filter any additional constraint. Nullable.
	 * @param collection if not {@code null}, then reuse this collection instead of the internal one. The behaviour
	 *                   is undefined is the collection is non-empty. Nullable.
	 * @return the query result.
	 */
	Collection<TrMember> resolveMethods(String name, String descPrefix, boolean isDescPrefix, Predicate<TrMember> filter, Collection<TrMember> collection);

	/**
	 * @see TrClass#resolveMethods(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> resolveMethods(String name, String descPrefix, boolean isDescPrefix, Collection<TrMember> collection) {
		return resolveMethods(name, descPrefix, isDescPrefix, m -> true, collection);
	}

	/**
	 * @see TrClass#resolveMethods(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> resolveMethods(String name, String desc, Collection<TrMember> collection) {
		return resolveMethods(name, desc, false, m -> true, collection);
	}

	/**
	 * @see TrClass#resolveMethods(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> resolveMethods(String name, Collection<TrMember> collection) {
		return resolveMethods(name, null, false, m -> true, collection);
	}

	/**
	 * @see TrClass#resolveMethods(String, String, boolean, Predicate, Collection)
	 */
	default TrMember resolveMethod(String name, String desc) {
		Collection<TrMember> member = resolveMethods(name, desc, null);

		if (member.size() == 1) {
			return member.stream().findAny().get();
		} else {
			return null;
		}
	}

	/**
	 * Get fields in the class, including the one inherited from super-class or super-interfaces, satisfy the search query.
	 * @param name the name of the field. Nullable.
	 * @param descPrefix the descriptor (or descriptor prefix) of the field. Nullable.
	 * @param isDescPrefix is {@code descPrefix} a full qualified descriptor or a prefix.
	 * @param filter any additional constraint. Nullable.
	 * @param collection if not {@code null}, then reuse this collection instead of allocate a new one. The behaviour
	 *                   is undefined is the collection is non-empty. Nullable.
	 * @return the query result.
	 */
	Collection<TrMember> resolveFields(String name, String descPrefix, boolean isDescPrefix, Predicate<TrMember> filter, Collection<TrMember> collection);

	/**
	 * @see TrClass#resolveFields(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> resolveFields(String name, String descPrefix, boolean isDescPrefix, Collection<TrMember> collection) {
		return resolveFields(name, descPrefix, isDescPrefix, m -> true, collection);
	}

	/**
	 * @see TrClass#resolveFields(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> resolveFields(String name, String desc, Collection<TrMember> collection) {
		return resolveFields(name, desc, false, m -> true, collection);
	}

	/**
	 * @see TrClass#resolveFields(String, String, boolean, Predicate, Collection)
	 */
	default Collection<TrMember> resolveFields(String name, Collection<TrMember> collection) {
		return resolveFields(name, null, false, m -> true, collection);
	}

	/**
	 * @see TrClass#resolveFields(String, String, boolean, Predicate, Collection)
	 */
	default TrMember resolveField(String name, String desc) {
		Collection<TrMember> member = resolveFields(name, desc, null);

		if (member.size() == 1) {
			return member.stream().findAny().get();
		} else {
			return null;
		}
	}

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
	 * Treat superclass methods specially when invoked by the {@link Opcodes#INVOKESPECIAL} instruction.
	 */
	default boolean isSuper() {
		return (getAccess() & Opcodes.ACC_SUPER) != 0;
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
