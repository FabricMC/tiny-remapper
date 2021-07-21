package net.fabricmc.tinyremapper.extension.mixin.common;

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
}
