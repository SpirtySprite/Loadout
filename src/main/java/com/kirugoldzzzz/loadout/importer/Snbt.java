package com.kirugoldzzzz.loadout.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Snbt {

    private Snbt() {
    }

    public record Item(String id, int count, String argument) {
    }

    public static Item item(String compound) {
        Map<String, String> entries = entries(compound);
        String id = unquote(entries.get("id"));
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("item has no id");
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        int count = number(entries.getOrDefault("count", entries.getOrDefault("Count", "1")));
        String components = entries.get("components");
        StringBuilder argument = new StringBuilder(id);
        if (components != null) {
            List<String> parts = new ArrayList<>();
            entries(components).forEach((key, value) -> {
                String name = unquote(key);
                parts.add(name.startsWith("!") ? name : name + "=" + value);
            });
            if (!parts.isEmpty()) {
                argument.append('[').append(String.join(",", parts)).append(']');
            }
        }
        return new Item(id, Math.max(1, count), argument.toString());
    }

    public static Map<String, String> entries(String compound) {
        String body = compound == null ? "" : compound.trim();
        if (body.length() < 2 || body.charAt(0) != '{' || body.charAt(body.length() - 1) != '}') {
            throw new IllegalArgumentException("not a compound: " + compound);
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (String part : split(body.substring(1, body.length() - 1))) {
            if (part.isBlank()) {
                continue;
            }
            int colon = keyEnd(part);
            if (colon < 0) {
                throw new IllegalArgumentException("entry without a key: " + part);
            }
            entries.put(part.substring(0, colon).trim(), part.substring(colon + 1).trim());
        }
        return entries;
    }

    static List<String> split(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            if (quote != 0) {
                if (current == '\\') {
                    index++;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            switch (current) {
                case '"', '\'' -> quote = current;
                case '{', '[' -> depth++;
                case '}', ']' -> depth--;
                case ',' -> {
                    if (depth == 0) {
                        parts.add(body.substring(start, index).trim());
                        start = index + 1;
                    }
                }
                default -> {
                }
            }
        }
        if (quote != 0 || depth != 0) {
            throw new IllegalArgumentException("unbalanced snbt");
        }
        parts.add(body.substring(start).trim());
        return parts;
    }

    private static int keyEnd(String entry) {
        char first = entry.isEmpty() ? 0 : entry.charAt(0);
        if (first == '"' || first == '\'') {
            for (int index = 1; index < entry.length(); index++) {
                char current = entry.charAt(index);
                if (current == '\\') {
                    index++;
                } else if (current == first) {
                    return entry.indexOf(':', index + 1);
                }
            }
            return -1;
        }
        return entry.indexOf(':');
    }

    static String unquote(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && (trimmed.charAt(0) == '"' || trimmed.charAt(0) == '\'')
                && trimmed.charAt(trimmed.length() - 1) == trimmed.charAt(0)) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\" + trimmed.charAt(0), String.valueOf(trimmed.charAt(0)));
        }
        return trimmed;
    }

    private static int number(String raw) {
        String digits = raw == null ? "" : raw.trim().replaceAll("[bBsSlL]$", "");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException invalid) {
            return 1;
        }
    }
}
