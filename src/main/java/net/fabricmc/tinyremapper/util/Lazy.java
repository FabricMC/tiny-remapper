package net.fabricmc.tinyremapper.util;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class Lazy<T> implements Supplier<T> {
	private Callable<T> callable;
	private T instance;

	public Lazy(Callable<T> callable) {
		this.callable = Objects.requireNonNull(callable, "Callable may not be null");
	}

	@Override
	public T get() {
		T instance = this.instance;
		Callable<T> supplier = this.callable;
		if(supplier != null) {
			try {
				this.instance = supplier.call();
			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
			this.callable = null;
		}
		return instance;
	}

	public boolean hasEvaluated() {
		return this.callable == null;
	}
}