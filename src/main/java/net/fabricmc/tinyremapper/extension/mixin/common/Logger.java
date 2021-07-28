package net.fabricmc.tinyremapper.extension.mixin.common;

public final class Logger {
	public enum Level {
		INFO, WARN, ERROR
	}

	private final Level level;

	public Logger() {
		this(Level.WARN);
	}

	public Logger(Level level) {
		this.level = level;
	}

	public void info(String message) {
		if (this.level.compareTo(Level.INFO) <= 0) {
			System.out.println("[INFO]  [MIXIN] " + message);
		}
	}

	public void warn(String message) {
		if (this.level.compareTo(Level.WARN) <= 0) {
			System.out.println(ANSI_YELLOW + "[WARN]  [MIXIN] " + ANSI_RESET + message);
		}
	}

	public void error(String message) {
		if (this.level.compareTo(Level.ERROR) <= 0) {
			System.out.println(ANSI_RED + "[ERROR] [MIXIN] " + ANSI_RESET + message);
		}
	}

	private static final String ANSI_RESET = "\u001B[0m";
	private static final String ANSI_BLACK = "\u001B[30m";
	private static final String ANSI_RED = "\u001B[31m";
	private static final String ANSI_GREEN = "\u001B[32m";
	private static final String ANSI_YELLOW = "\u001B[33m";
	private static final String ANSI_BLUE = "\u001B[34m";
	private static final String ANSI_PURPLE = "\u001B[35m";
	private static final String ANSI_CYAN = "\u001B[36m";
	private static final String ANSI_WHITE = "\u001B[37m";
}
