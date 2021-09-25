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

package net.fabricmc.tinyremapper.extension.mixin.hard.data;

public final class SoftInterface {
	public enum Remap {
		NONE, ONLY_PREFIX, ALL, FORCE
	}

	private String target;
	private String prefix;
	private Remap remap;

	public String getTarget() {
		return target;
	}

	public String getPrefix() {
		return prefix;
	}

	public Remap getRemap() {
		return remap;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public void setRemap(Remap remap) {
		this.remap = remap;
	}

	@Override
	public String toString() {
		return "Interface{"
				+ "target='" + target + '\''
				+ ", prefix='" + prefix + '\''
				+ ", remap=" + remap
				+ '}';
	}
}
