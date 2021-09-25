/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2019, 2021, FabricMC
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum NonClassCopyMode {
	UNCHANGED(),
	FIX_META_INF(MetaInfFixer.INSTANCE),
	SKIP_META_INF(MetaInfRemover.INSTANCE);

	public final List<OutputConsumerPath.ResourceRemapper> remappers;

	NonClassCopyMode(OutputConsumerPath.ResourceRemapper...remappers) {
		this.remappers = Collections.unmodifiableList(Arrays.asList(remappers));
	}
}
