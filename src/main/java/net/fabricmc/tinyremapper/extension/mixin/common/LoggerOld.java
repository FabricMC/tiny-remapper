package net.fabricmc.tinyremapper.extension.mixin.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Deprecated
public final class LoggerOld {
	public enum Level {
		WARN, ERROR
	}

	public static void error(String message) {
		if (level.equals(Level.WARN) || level.equals(Level.ERROR)) {
			System.out.println(ANSI_RED + "[ERROR] [MIXIN] " + ANSI_RESET + message);
		}
	}

	public static void warn(String message) {
		if (level.equals(Level.WARN)) {
			System.out.println(ANSI_YELLOW + "[WARN]  [MIXIN] " + ANSI_RESET + message);
		}
	}

	public static void remapFail(String annotation, String target, String className) {
		if (isPrint(target)) {
			warn(ANSI_RED + annotation + ANSI_RESET
					+ " remap fail for target " + target + " on mixin class " + className);
		}
	}

	public static void remapFail(String annotation, List<String> targets, String className, String memberName) {
		if (isPrint(targets, memberName)) {
			warn(ANSI_RED + annotation + ANSI_RESET
					+ " remap fail for one of targets " + targets.toString()
					+ " on member " + memberName + " inside mixin class " + className);
		}
	}

	private static Level level = Level.WARN;

	public static void setLogLevel(Level level) {
		LoggerOld.level = level;
	}

	private static final List<Pattern> ignoreTargets = new ArrayList<>();
	private static final List<Pair<Pattern, Pattern>> ignoreMembers = new ArrayList<>();

	public static void suppressRemapFail(String target) {
		LoggerOld.ignoreTargets.add(Pattern.compile(target));
	}

	public static void suppressRemapFail(String target, String member) {
		LoggerOld.ignoreMembers.add(Pair.of(Pattern.compile(target), Pattern.compile(member)));
	}

	public static void resetSuppressRemapFail() {
		LoggerOld.ignoreTargets.clear();
		LoggerOld.ignoreMembers.clear();
	}

	private static boolean isPrint(String target) {
		return LoggerOld.ignoreTargets.stream().noneMatch(x -> x.matcher(target).matches());
	}

	private static boolean isPrint(List<String> targets, String member) {
		return LoggerOld.ignoreMembers.stream().noneMatch(
				x -> targets.stream().anyMatch(
						target -> x.first().matcher(target).matches()
				) && x.second().matcher(member).matches());
	}

	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_BLACK = "\u001B[30m";
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_BLUE = "\u001B[34m";
	public static final String ANSI_PURPLE = "\u001B[35m";
	public static final String ANSI_CYAN = "\u001B[36m";
	public static final String ANSI_WHITE = "\u001B[37m";
}
