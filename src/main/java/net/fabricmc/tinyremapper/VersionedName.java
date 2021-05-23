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

package net.fabricmc.tinyremapper;

import java.io.File;
import java.util.Objects;
import java.util.OptionalInt;

public class VersionedName {
    public static final String MRJ_PREFIX = File.separator + "META-INF" + File.separator + "versions";

    private final String name;
    private final OptionalInt version;

    public VersionedName(String name, OptionalInt version) {
        this.name = name;
        this.version = version;
    }

    public String getName() { return name; }
    public OptionalInt getVersion() { return version; }

    public static String getMultiReleaseClassName(String clsName, OptionalInt mrjVersion) {
        if (mrjVersion.isPresent()) {
            return MRJ_PREFIX + File.separator + mrjVersion.getAsInt() + File.separator + clsName;
        } else {
            return clsName;
        }
    }

    public String getMultiReleaseClassName() {
        return getMultiReleaseClassName(name, version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionedName that = (VersionedName) o;
        return name.equals(that.name) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }
}