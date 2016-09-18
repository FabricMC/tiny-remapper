package net.fabricmc.tinyremapper;

import java.util.Map;

@FunctionalInterface
public interface IMappingProvider {
    void load(Map<String, String> classMap, Map<String, String> fieldMap, Map<String, String> methodMap);
}
