/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, FabricMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper.extension.mixin.common;

public final class Logger {
	public enum Level {
		INFO, WARN, ERROR
	}

	private final Level level;

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
