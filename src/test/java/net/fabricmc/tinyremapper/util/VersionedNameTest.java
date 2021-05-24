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

import java.util.OptionalInt;

import static org.junit.Assert.*;

public class VersionedNameTest {

    private final static String NAME_1 = "/net/fabricmc/tinyremapper/ClassInstance";
    private final static String NAME_2 = "/net/fabricmc/tinyremapper/AsmClassRemapper";

    @Test
    public void getName() {
        VersionedName o1 = new VersionedName(NAME_1, OptionalInt.of(1));

        assertEquals(NAME_1, o1.getName());
    }

    @Test
    public void getVersion() {
        VersionedName o1 = new VersionedName(NAME_1, OptionalInt.of(1));
        VersionedName o2 = new VersionedName(NAME_1, OptionalInt.empty());

        assertEquals(OptionalInt.of(1), o1.getVersion());
        assertEquals(OptionalInt.empty(), o2.getVersion());
    }

    @Test
    public void testEquals() {
        VersionedName o1 = new VersionedName(NAME_1, OptionalInt.of(1));
        VersionedName o2 = new VersionedName(NAME_1, OptionalInt.of(1));
        VersionedName o3 = new VersionedName(NAME_2, OptionalInt.of(1));
        VersionedName o4 = new VersionedName(NAME_1, OptionalInt.empty());

        assertEquals(o1, o2);

        assertNotEquals(o1, o3);
        assertNotEquals(o1, o4);
        assertNotEquals(o3, o4);
    }
}