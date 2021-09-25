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

package net.fabricmc.tinyremapper.extension.mixin.hard.util;

import java.util.Objects;
import java.util.Optional;

import net.fabricmc.tinyremapper.extension.mixin.common.IMappable;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxMember;

public abstract class HardTargetMappable implements IMappable<Void> {
	protected CommonData data;
	protected MxMember self;

	public HardTargetMappable(CommonData data, MxMember self) {
		this.data = Objects.requireNonNull(data);
		this.self = Objects.requireNonNull(self);
	}

	protected abstract Optional<String> getMappedName();

	@Override
	public Void result() {
		getMappedName().ifPresent(x -> data.propagate(self.asTrMember(data.resolver), x));
		return null;
	}
}
