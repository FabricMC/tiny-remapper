package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrField;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.api.TrMethod;

public final class Resolver {
	/**
	 * Raise error if the result is not unique.
	 */
	public static int FLAG_UNIQUE = 0x1;
	/**
	 * Return first member based on asm ordinal if the result is not unique.
	 */
	public static int FLAG_FIRST = 0x2;
	/**
	 * Recursively resolve the the member including super-class and super-interface.
	 */
	public static int FLAG_RECURSIVE = 0x4;
	/**
	 * Prefer non-synthetic member. This has higher priority than {@link Resolver#FLAG_FIRST};
	 */
	public static int FLAG_NON_SYN = 0x8;

	private final TrEnvironment environment;
	private final Logger logger;

	public Resolver(TrEnvironment environment, Logger logger) {
		this.environment = Objects.requireNonNull(environment);
		this.logger = Objects.requireNonNull(logger);
	}

	public Optional<TrMember> resolve(TrClass owner, String name, String desc, int flag) {
		Objects.requireNonNull(owner);
		Objects.requireNonNull(name);
		Objects.requireNonNull(desc);

		if (StringUtility.isFieldDesc(desc)) {
			return (flag & FLAG_RECURSIVE) != 0
					? Optional.ofNullable(owner.resolveField(name, desc)) : Optional.ofNullable(owner.getField(name, desc));
		} else if (StringUtility.isMethodDesc(desc)) {
			return (flag & FLAG_RECURSIVE) != 0
					? Optional.ofNullable(owner.resolveMethod(name, desc)) : Optional.ofNullable(owner.getMethod(name, desc));
		} else {
			throw new RuntimeException("Invalid descriptor detected " + desc);
		}
	}

	public Optional<TrMember> resolve(String owner, String name, String desc, int flag) {
		return resolve(environment.getClass(owner), name, desc, flag);
	}

	private <T extends TrMember> Optional<TrMember> resolve0(TrClass owner, String name, Consumer<Collection<T>> getter, int flag) {
		List<T> collection = new ArrayList<>();
		getter.accept(collection);

		if ((flag & (FLAG_UNIQUE | FLAG_FIRST)) == 0) {
			throw new RuntimeException("Unspecified resolution strategy");
		}

		if ((flag & FLAG_UNIQUE) != 0) {
			if (collection.size() > 1) {
				logger.error("Ambiguous member resolution for name " + name + " in class " + owner.getName() + ". " + collection);
				return Optional.empty();
			} else {
				return collection.stream().findFirst().map(x -> x);
			}
		}

		Comparator<T> comparator;

		if ((flag & FLAG_NON_SYN) != 0) {
			comparator = (x, y) -> Boolean.compare(x.isSynthetic(), y.isSynthetic()) == 0
					? Boolean.compare(x.isSynthetic(), y.isSynthetic()) : Integer.compare(x.getIndex(), y.getIndex());
		} else {
			comparator = Comparator.comparingInt(TrMember::getIndex);
		}

		return collection.stream().min(comparator).map(x -> x);
	}

	public Optional<TrMember> resolve(TrClass owner, String name, MemberType type, int flag) {
		if (type.equals(MemberType.FIELD)) {
			if ((flag & FLAG_RECURSIVE) != 0) {
				return this.resolve0(owner, name, (Collection<TrField> out0) -> owner.resolveFields(name, null, false, null, out0), flag);
			} else {
				return this.resolve0(owner, name, (Collection<TrField> out0) -> owner.getFields(name, null, false, null, out0), flag);
			}
		} else if (type.equals(MemberType.METHOD)) {
			if ((flag & FLAG_RECURSIVE) != 0) {
				return this.resolve0(owner, name, (Collection<TrMethod> out0) -> owner.resolveMethods(name, null, false, null, out0), flag);
			} else {
				return this.resolve0(owner, name, (Collection<TrMethod> out0) -> owner.getMethods(name, null, false, null, out0), flag);
			}
		} else {
			throw new RuntimeException("Unknown member type " + type.name());
		}
	}

	public Optional<TrMember> resolve(String owner, String name, MemberType type, int flag) {
		return resolve(environment.getClass(owner), name, type, flag);
	}
}
