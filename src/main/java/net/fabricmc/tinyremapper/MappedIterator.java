package net.fabricmc.tinyremapper;

import java.util.Iterator;
import java.util.function.Function;

public final class MappedIterator<F, T> implements Iterator<T> {
	private final Iterator<F> from;
	private final Function<F, T> function;

	public static <F, T> Iterable<T> map(Iterable<F> from, Function<F, T> map) {
		return () -> new MappedIterator<>(from.iterator(), map);
	}

	public MappedIterator(Iterator<F> from, Function<F, T> function) {
		this.from = from;
		this.function = function;
	}

	@Override
	public boolean hasNext() {
		return this.from.hasNext();
	}

	@Override
	public T next() {
		return this.function.apply(this.from.next());
	}
}
