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

package net.fabricmc.tinyremapper;

import net.fabricmc.tinyremapper.api.TrLogger;

public final class ConsoleLogger implements TrLogger {
	private final TrLogger.Level level;

	public ConsoleLogger(TrLogger.Level level) {
		this.level = level;
	}

	public ConsoleLogger() {
		this(TrLogger.Level.INFO);
	}

	@Override
	public void log(Level level, String message) {
		if (this.level.compareTo(level) <= 0) {
			System.out.println("[" + level + "] " + message);
		}
	}
}
