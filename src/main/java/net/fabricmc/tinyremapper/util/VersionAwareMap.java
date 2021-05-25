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

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.fabricmc.tinyremapper.ClassInstance;

import java.util.*;
import java.util.stream.Collectors;

public class VersionAwareMap implements Map<VersionedName, ClassInstance> {
    private final Multimap<String, ClassInstance> storage;

    public VersionAwareMap() {
        storage = MultimapBuilder.hashKeys().arrayListValues().build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return storage.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return storage.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return storage.containsValue(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClassInstance get(Object key) {
        if (key instanceof VersionedName) {
            String name = ((VersionedName) key).getName();
            return storage.get(name).stream()
                    .filter(cls -> cls.getVersionedName().equals(key))
                    .findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Return a collection of {@code ClassInstance} with name {@code key}
     * @param key the name of {@code ClassInstance}
     * @return all {@code ClassInstance} with name {@code key}
     */
    public Collection<ClassInstance> getAllVersionClasses(String key) {
        return new ArrayList<>(storage.get(key));
    }

    /**
     * Return a collection of {@code ClassInstance} with name {@code key.getName()}
     * and whose MRJ version is less than or equal to {@code key.getVersion()}.
     *
     * <p> Specifically, {@code OptionalInt.empty()} is consider to be always
     * less than any MRJ version. If none of {@code ClassInstance} is available,
     * then a empty collection is returned. </p>
     *
     * @param key the name of {@code ClassInstance}
     * @return all {@code ClassInstance} with name {@code key.getName()} and MRJ version
     * is less than or equal to {@code key.getVersion()}
     */
    public Collection<ClassInstance> getFeasibleVersionClasses(VersionedName key) {
        return getFeasibleVersionClasses(key.getName(), key.getVersion());
    }

    public Collection<ClassInstance> getFeasibleVersionClasses(String name, int version) {
        return storage.get(name).stream()
                .filter(cls -> cls.getVersionedName().getVersion() < version)
                .collect(Collectors.toList());
    }

    /**
     * Return a single {@code ClassInstance} whose MRJ version is the largest
     * among all candidates.
     *
     * <p> A candidate is any {@code ClassInstance} returned by method
     * {@code getFeasibleVersions}. {@code null} will be returned if no
     * candidate available. </p>
     *
     * @param key the name of {@code ClassInstance}
     * @return a {@code ClassInstance} that is a candidate and MRJ version is the largest
     */
    public ClassInstance getByVersion(VersionedName key) {
        return getByVersion(key.getName(), key.getVersion());
    }

    public ClassInstance getByVersion(String name, int version) {
        return getFeasibleVersionClasses(name, version).stream()
                .max(Comparator.comparingInt(o -> o.getVersionedName().getVersion()))
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClassInstance put(VersionedName key, ClassInstance value) {
        ClassInstance result = remove(key);
        storage.get(key.getName()).add(value);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClassInstance remove(Object key) {
        ClassInstance result = get(key);

        if (key instanceof VersionedName) {
            String name = ((VersionedName) key).getName();
            storage.get(name).removeIf(cls -> cls.getVersionedName().equals(key));
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends VersionedName, ? extends ClassInstance> m) {
        m.forEach(this::put);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        storage.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<VersionedName> keySet() {
        return values().stream()
                .map(ClassInstance::getVersionedName)
                .collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<ClassInstance> values() {
        return storage.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<VersionedName, ClassInstance>> entrySet() {
        return values().stream()
                .map(cls -> new AbstractMap.SimpleEntry<>(cls.getVersionedName(), cls))
                .collect(Collectors.toSet());
    }

    public Set<Integer> versions() {
        return keySet().stream()
                .map(VersionedName::getVersion)
                .collect(Collectors.toSet());
    }

    public Set<String> names() {
        return storage.keySet();
    }
}