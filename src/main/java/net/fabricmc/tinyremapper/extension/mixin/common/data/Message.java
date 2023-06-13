/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, 2023, FabricMC
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

package net.fabricmc.tinyremapper.extension.mixin.common.data;

public final class Message {
	public static final String CANNOT_RESOLVE_CLASS = "Cannot resolve class %s";
	public static final String CONFLICT_MAPPING = "Conflict mapping detected, %s -> %s.";
	public static final String MULTIPLE_MAPPING_CHOICES = "Multiple conflicting mapping choices found for %s, which can be remapped to %s or %s. Such issues can be resolved by using fully qualified selectors.";
	public static final String NO_MAPPING_NON_RECURSIVE = "Cannot remap %s because it does not exists in any of the targets %s";
	public static final String NO_MAPPING_RECURSIVE = "Cannot remap %s because it does not exists in any of the targets %s or their parents.";
	public static final String NOT_FULLY_QUALIFIED = "%s is not fully qualified.";
}
