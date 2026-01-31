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

package net.fabricmc.tinyremapper;

import net.fabricmc.tinyremapper.api.TrLocal;
import net.fabricmc.tinyremapper.api.TrMethod;

public class LocalInstance implements TrLocal {
	public LocalInstance(TrMethod owner, String name, String desc, int index) {
		this.owner = owner;
		this.name = name;
		this.desc = desc;
		this.index = index;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getDesc() {
		return this.desc;
	}

	@Override
	public int getIndex() {
		return this.index;
	}

	@Override
	public TrMethod getOwner() {
		return this.owner;
	}

	final TrMethod owner;
	final String name;
	final String desc;
	final int index;
}
