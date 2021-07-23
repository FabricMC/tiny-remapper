package net.fabricmc.tinyremapper.api;

@FunctionalInterface
public interface StateProcessor {
	void process(TrEnvironment env);
}
