package com.kirugoldzzzz.loadout.common.text;

import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class Tr {

    public static final String SOURCE_LANGUAGE = "fr";

    private static volatile String language = SOURCE_LANGUAGE;
    private static volatile Map<String, String> translations = Map.of();

    private Tr() {
    }

    public static void configure(Plugin plugin, String requested) {
        String code = requested == null || requested.isBlank() ? "en" : requested.trim().toLowerCase(Locale.ROOT);
        language = code;
        if (code.equals(SOURCE_LANGUAGE)) {
            translations = Map.of();
            return;
        }
        Map<String, String> merged = new HashMap<>();
        String resource = "lang/" + code + ".yml";
        try (InputStream bundled = plugin.getResource(resource)) {
            if (bundled != null) {
                merged.putAll(read(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
            }
        } catch (Exception failure) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + resource, failure);
        }
        File custom = new File(plugin.getDataFolder(), resource);
        if (!custom.exists() && plugin.getResource(resource) != null) {
            plugin.saveResource(resource, false);
        }
        if (custom.isFile()) {
            try (Reader reader = Files.newBufferedReader(custom.toPath(), StandardCharsets.UTF_8)) {
                merged.putAll(read(reader));
            } catch (Exception failure) {
                plugin.getLogger().log(Level.WARNING, "Could not read " + custom.getPath(), failure);
            }
        }
        translations = Map.copyOf(merged);
    }

    private static Map<String, String> read(Reader reader) {
        Object loaded = new Yaml().load(reader);
        Map<String, String> result = new HashMap<>();
        if (loaded instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        return result;
    }

    public static String t(String source) {
        if (source == null) {
            return null;
        }
        Map<String, String> current = translations;
        if (current.isEmpty()) {
            return source;
        }
        return current.getOrDefault(source, source);
    }

    public static String messagesFile(Plugin plugin) {
        String wanted = "lang/messages_" + language + ".yml";
        if (plugin.getResource(wanted) != null || new File(plugin.getDataFolder(), wanted).isFile()) {
            return wanted;
        }
        return "lang/messages_en.yml";
    }

    public static void seedLocalized(Plugin plugin, String fileName) {
        File target = new File(plugin.getDataFolder(), fileName);
        if (target.exists()) {
            return;
        }
        int dot = fileName.lastIndexOf('.');
        String localized = "lang/" + (dot < 0 ? fileName : fileName.substring(0, dot)) + "_" + language
                + (dot < 0 ? "" : fileName.substring(dot));
        try (InputStream stream = plugin.getResource(localized)) {
            if (stream == null) {
                return;
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            Files.copy(stream, target.toPath());
        } catch (Exception failure) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + fileName + " in " + language, failure);
        }
    }

    public static String language() {
        return language;
    }

    public static int size() {
        return translations.size();
    }
}
