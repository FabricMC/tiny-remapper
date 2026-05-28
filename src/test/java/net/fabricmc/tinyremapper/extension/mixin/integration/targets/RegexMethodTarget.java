/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2026, FabricMC
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

package net.fabricmc.tinyremapper.extension.mixin.integration.targets;

import java.util.List;

public class RegexMethodTarget {
	private String target0() {
		return "target0";
	}

	private String target0(String input) {
		return input;
	}

	private String target1() {
		return "target1";
	}

	private String target2(List<String> input) {
		return input.toString();
	}

	private void target3(String input) {
	}

	private String thing4() {
		return "thing4";
	}
}
