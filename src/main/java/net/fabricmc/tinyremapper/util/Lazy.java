package net.fabricmc.tinyremapper.util;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class Lazy<T> implements Supplier<T> {
	private Callable<T> supplier;
	private T instance;

	public Lazy(Callable<T> supplier) {
		this.supplier = Objects.requireNonNull(supplier, "Supplier may not be null");
	}

	@Override
	public T get() {
		T instance = this.instance;
		Callable<T> supplier = this.supplier;
		if(supplier != null) {
			try {
				this.instance = supplier.call();
			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
			this.supplier = null;
		}
		return instance;
	}

	public boolean hasEvaluated() {
		return this.supplier == null;
	}
}