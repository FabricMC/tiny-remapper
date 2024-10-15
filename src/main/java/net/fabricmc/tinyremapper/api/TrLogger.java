/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2019, 2022, FabricMC
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

package net.fabricmc.tinyremapper.api;

public interface TrLogger {
	void log(Level level, String message);

	default void log(Level level, String message, Object... args) {
		log(level, String.format(message, args));
	}

	default void debug(String message) {
		log(Level.DEBUG, message);
	}

	default void debug(String message, Object... args) {
		log(Level.DEBUG, message, args);
	}

	default void info(String message) {
		log(Level.INFO, message);
	}

	default void info(String message, Object... args) {
		log(Level.INFO, message, args);
	}

	default void warn(String message) {
		log(Level.WARN, message);
	}

	default void warn(String message, Object... args) {
		log(Level.WARN, message, args);
	}

	default void error(String message) {
		log(Level.ERROR, message);
	}

	default void error(String message, Object... args) {
		log(Level.ERROR, message, args);
	}

	enum Level {
		DEBUG,
		INFO,
		WARN,
		ERROR
	}
}
