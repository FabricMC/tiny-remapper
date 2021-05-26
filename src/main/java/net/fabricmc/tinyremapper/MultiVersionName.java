package net.fabricmc.tinyremapper;

import java.util.Objects;

public class MultiVersionName {
    private final String name;
    private final int version;

    public static final int DEFAULT_VERSION = -1;

    public MultiVersionName(String name, int version) {
        this.name = name;
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultiVersionName that = (MultiVersionName) o;
        return version == that.version && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }
}
