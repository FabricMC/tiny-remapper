package net.fabricmc.tinyremapper.extension.mixin.common.data;

import java.util.Objects;

public final class Pair<L, R> {
	private final L first;
	private final R second;

	private Pair(L first, R second) {
		this.first = first;
		this.second = second;
	}

	public static <L, R> Pair<L, R> of(final L first, final R second) {
		return new Pair<>(first, second);
	}

	public L first() {
		return this.first;
	}

	public R second() {
		return this.second;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Pair<?, ?> pair = (Pair<?, ?>) o;

		if (!Objects.equals(first, pair.first)) return false;
		return Objects.equals(second, pair.second);
	}

	@Override
	public int hashCode() {
		int result = first != null ? first.hashCode() : 0;
		result = 31 * result + (second != null ? second.hashCode() : 0);
		return result;
	}
}
