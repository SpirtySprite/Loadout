package com.kirugoldzzzz.loadout.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Imported {

    private Imported() {
    }

    public record Item(Map<String, Object> spec, String argument, String serialized, int amount) {

        public static Item spec(Map<String, Object> spec, int amount) {
            return new Item(Map.copyOf(spec), null, null, Math.max(1, amount));
        }

        public static Item argument(String argument, int amount) {
            return new Item(Map.of(), argument, null, Math.max(1, amount));
        }

        public static Item serialized(String base64) {
            return new Item(Map.of(), null, base64, 1);
        }

        public String material() {
            if (argument != null) {
                String id = argument.contains("[") ? argument.substring(0, argument.indexOf('[')) : argument;
                return id.substring(id.indexOf(':') + 1);
            }
            Object material = spec.get("material");
            return material == null ? "" : material.toString();
        }
    }

    public record Kit(String id, String name, long cooldownSeconds, boolean once, double price, List<Item> items,
                      List<String> commands, double money) {
    }

    public static final class Result {

        private final String source;
        private final List<Kit> kits = new ArrayList<>();
        private final Map<UUID, Map<String, Long>> claims = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();

        public Result(String source) {
            this.source = source;
        }

        public String source() {
            return source;
        }

        public List<Kit> kits() {
            return kits;
        }

        public Map<UUID, Map<String, Long>> claims() {
            return claims;
        }

        public List<String> warnings() {
            return warnings;
        }

        public void add(Kit kit) {
            kits.add(kit);
        }

        public void claim(UUID player, String kit, long at) {
            if (at > 0L) {
                claims.computeIfAbsent(player, ignored -> new LinkedHashMap<>()).merge(kit, at, Math::max);
            }
        }

        public void warn(String warning) {
            warnings.add(warning);
        }
    }
}
