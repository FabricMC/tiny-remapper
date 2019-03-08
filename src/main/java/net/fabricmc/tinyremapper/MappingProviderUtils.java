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

import net.fabricmc.mappings.*;

public final class MappingProviderUtils {
    private MappingProviderUtils() {

    }

    private static String entryToValueString(EntryTriple triple) {
        return triple.getOwner() + "/" + triple.getName();
    }

    private static String fieldToString(EntryTriple triple) {
        return triple.getOwner() + "/" + triple.getName() + ";;" + triple.getDesc();
    }

    private static String methodToString(EntryTriple triple) {
        return triple.getOwner() + "/" + triple.getName() + triple.getDesc();
    }

    public static IMappingProvider create(Mappings mappings, String from, String to) {
        return (classMap, fieldMap, methodMap) -> {
            for (ClassEntry entry : mappings.getClassEntries()) {
                classMap.put(entry.get(from), entry.get(to));
            }

            for (FieldEntry entry : mappings.getFieldEntries()) {
                fieldMap.put(fieldToString(entry.get(from)), entryToValueString(entry.get(to)));
            }

            for (MethodEntry entry : mappings.getMethodEntries()) {
                methodMap.put(methodToString(entry.get(from)), entryToValueString(entry.get(to)));
            }
        };
    }
}
