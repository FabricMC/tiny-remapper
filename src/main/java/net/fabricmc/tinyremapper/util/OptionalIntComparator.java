/*
 * Copyright (C) 2016, 2018 Player, asie
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

package net.fabricmc.tinyremapper.util;

import java.util.Comparator;
import java.util.OptionalInt;

public class OptionalIntComparator implements Comparator<OptionalInt> {
    @Override
    public int compare(OptionalInt o1, OptionalInt o2) {
        if (o1.equals(o2)) {
            return 0;
        } else if (!o1.isPresent()) {
            return -1;
        } else if (!o2.isPresent()) {
            return 1;
        } else {
            return Integer.compare(o1.getAsInt(), o2.getAsInt());
        }
    }
}
