package com.kirugoldzzzz.loadout.common.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Sections {

    private Sections() {
    }

    public static void copy(ConfigurationSection source, ConfigurationSection target) {
        for (Map.Entry<String, Object> entry : source.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) {
                if (!target.isConfigurationSection(entry.getKey())) {
                    target.createSection(entry.getKey());
                }
            } else {
                target.set(entry.getKey(), copyValue(entry.getValue()));
            }
        }
    }

    public static Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(copyValue(element)));
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, element) -> copy.put(key, copyValue(element)));
            return copy;
        }
        return value;
    }
}
