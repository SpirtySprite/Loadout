package com.kirugoldzzzz.loadout.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.UUID;

public final class Messages {

    private static volatile Map<String, String> VALUES = Map.of();
    private static volatile Map<String, List<String>> LISTS = Map.of();
    private static volatile Map<String, Component> RESOLVED = new ConcurrentHashMap<>();

    private static volatile TagResolver prefixResolver = Mini.styled("prefix", "");
    private static volatile Predicate<UUID> broadcastFilter = ignored -> true;

    private Messages() {
    }

    public static void load(ConfigurationSection root) {
        Map<String, String> values = new HashMap<>();
        Map<String, List<String>> lists = new HashMap<>();
        if (root != null) {
            flatten(root, "", values, lists);
        }
        VALUES = Map.copyOf(values);
        LISTS = Map.copyOf(lists);
        prefixResolver = Mini.styled("prefix", values.getOrDefault("prefix", ""));
        Mini.invalidate();
        RESOLVED = new ConcurrentHashMap<>();
    }

    private static void flatten(ConfigurationSection section, String path, Map<String, String> values,
                                Map<String, List<String>> lists) {
        for (String key : section.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                flatten(nested, fullPath, values, lists);
            } else if (value instanceof List<?> list) {
                List<String> lines = new ArrayList<>(list.size());
                list.forEach(entry -> lines.add(String.valueOf(entry)));
                lists.put(fullPath, List.copyOf(lines));
            } else if (value != null) {
                values.put(fullPath, String.valueOf(value));
            }
        }
    }

    public static String raw(String key) {
        return VALUES.getOrDefault(key, key);
    }

    public static boolean has(String key) {
        return VALUES.containsKey(key) || LISTS.containsKey(key);
    }

    public static Component get(String key, TagResolver... resolvers) {
        if (resolvers.length == 0) {
            return RESOLVED.computeIfAbsent(key,
                    lookup -> Mini.parse(raw(lookup), withPrefix(resolvers)));
        }
        return Mini.parse(raw(key), withPrefix(resolvers));
    }

    public static List<Component> lines(String key, TagResolver... resolvers) {
        TagResolver[] merged = withPrefix(resolvers);
        List<String> raw = LISTS.get(key);
        if (raw == null) {
            return List.of(get(key, resolvers));
        }
        List<Component> components = new ArrayList<>(raw.size());
        for (String line : raw) {
            components.add(Mini.label(line, merged));
        }
        return components;
    }

    public static void send(CommandSender sender, String key, TagResolver... resolvers) {
        String template = VALUES.get(key);
        if (template == null) {
            if (LISTS.containsKey(key)) {
                lines(key, resolvers).forEach(sender::sendMessage);
            } else {
                sender.sendMessage(Mini.parse(key, withPrefix(resolvers)));
            }
            return;
        }
        if (template.isBlank()) {
            return;
        }
        if (resolvers.length == 0) {
            sender.sendMessage(RESOLVED.computeIfAbsent(key,
                    ignored -> Mini.parse(template, withPrefix(resolvers))));
            return;
        }
        sender.sendMessage(Mini.parse(template, withPrefix(resolvers)));
    }

    public static void broadcast(String key, TagResolver... resolvers) {
        String template = VALUES.get(key);
        if (template == null) {
            if (!LISTS.containsKey(key)) {
                return;
            }
            List<Component> rendered = lines(key, resolvers);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (broadcastFilter.test(player.getUniqueId())) rendered.forEach(player::sendMessage);
            }
            return;
        }
        if (template.isBlank()) {
            return;
        }
        Component message = Mini.parse(template, withPrefix(resolvers));
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (broadcastFilter.test(player.getUniqueId())) player.sendMessage(message);
        }
    }

    public static void broadcastPermission(String permission, String key, TagResolver... resolvers) {
        broadcastTo(player -> player.hasPermission(permission), key, resolvers);
    }

    public static void broadcastTo(Predicate<Player> audience, String key, TagResolver... resolvers) {
        String template = VALUES.get(key);
        List<Component> rendered;
        if (template == null) {
            if (!LISTS.containsKey(key)) {
                return;
            }
            rendered = lines(key, resolvers);
        } else if (template.isBlank()) {
            return;
        } else {
            rendered = List.of(Mini.parse(template, withPrefix(resolvers)));
        }
        rendered.forEach(Bukkit.getConsoleSender()::sendMessage);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (audience.test(player)) {
                rendered.forEach(player::sendMessage);
            }
        }
    }

    public static void broadcastFilter(Predicate<UUID> filter) {
        broadcastFilter = filter == null ? ignored -> true : filter;
    }

    private static TagResolver[] withPrefix(TagResolver[] resolvers) {
        if (resolvers.length == 0) {
            return new TagResolver[]{prefixResolver};
        }
        TagResolver[] merged = Arrays.copyOf(resolvers, resolvers.length + 1);
        merged[resolvers.length] = prefixResolver;
        return merged;
    }
}
