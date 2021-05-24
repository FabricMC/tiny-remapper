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

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class OptionalIntComparatorTest {

    @Test
    public void compare() {
        Comparator<OptionalInt> comp = new OptionalIntComparator();

        OptionalInt o1 = OptionalInt.empty();
        OptionalInt o2 = OptionalInt.of(1);
        OptionalInt o3 = OptionalInt.of(2);

        assertEquals(0, comp.compare(o1, o1));
        assertEquals(0, comp.compare(o2, o2));

        assertEquals(-1, comp.compare(o1, o2));
        assertEquals(-1, comp.compare(o2, o3));

        assertEquals(1, comp.compare(o2, o1));
        assertEquals(1, comp.compare(o3, o2));

        List<OptionalInt> list = Arrays.asList(
                OptionalInt.of(1), OptionalInt.of(3), OptionalInt.empty(), OptionalInt.of(2)
        );
        list.sort(comp);
        assertArrayEquals(list.toArray(), new OptionalInt[]{
                OptionalInt.empty(), OptionalInt.of(1), OptionalInt.of(2), OptionalInt.of(3)
        });
    }
}