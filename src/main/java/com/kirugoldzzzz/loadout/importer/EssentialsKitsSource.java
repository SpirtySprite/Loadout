package com.kirugoldzzzz.loadout.importer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

public final class EssentialsKitsSource implements KitSource {

    static final Map<String, String> ENCHANTMENTS = Map.ofEntries(
            Map.entry("durability", "unbreaking"),
            Map.entry("unbreaking", "unbreaking"),
            Map.entry("digspeed", "efficiency"),
            Map.entry("dig_speed", "efficiency"),
            Map.entry("efficiency", "efficiency"),
            Map.entry("damage_all", "sharpness"),
            Map.entry("sharpness", "sharpness"),
            Map.entry("damage_undead", "smite"),
            Map.entry("smite", "smite"),
            Map.entry("damage_arthropods", "bane_of_arthropods"),
            Map.entry("baneofarthropods", "bane_of_arthropods"),
            Map.entry("loot_bonus_blocks", "fortune"),
            Map.entry("fortune", "fortune"),
            Map.entry("loot_bonus_mobs", "looting"),
            Map.entry("looting", "looting"),
            Map.entry("protection_environmental", "protection"),
            Map.entry("protection", "protection"),
            Map.entry("protection_fire", "fire_protection"),
            Map.entry("fireprotection", "fire_protection"),
            Map.entry("protection_fall", "feather_falling"),
            Map.entry("featherfalling", "feather_falling"),
            Map.entry("protection_explosions", "blast_protection"),
            Map.entry("blastprotection", "blast_protection"),
            Map.entry("protection_projectile", "projectile_protection"),
            Map.entry("projectileprotection", "projectile_protection"),
            Map.entry("oxygen", "respiration"),
            Map.entry("respiration", "respiration"),
            Map.entry("water_worker", "aqua_affinity"),
            Map.entry("aquaaffinity", "aqua_affinity"),
            Map.entry("arrow_damage", "power"),
            Map.entry("power", "power"),
            Map.entry("arrow_knockback", "punch"),
            Map.entry("punch", "punch"),
            Map.entry("arrow_fire", "flame"),
            Map.entry("flame", "flame"),
            Map.entry("arrow_infinite", "infinity"),
            Map.entry("infinity", "infinity"),
            Map.entry("luck", "luck_of_the_sea"),
            Map.entry("luckofthesea", "luck_of_the_sea"),
            Map.entry("lure", "lure"),
            Map.entry("fire_aspect", "fire_aspect"),
            Map.entry("fireaspect", "fire_aspect"),
            Map.entry("knockback", "knockback"),
            Map.entry("silk_touch", "silk_touch"),
            Map.entry("silktouch", "silk_touch"),
            Map.entry("thorns", "thorns"),
            Map.entry("mending", "mending"),
            Map.entry("sweeping", "sweeping_edge"),
            Map.entry("sweeping_edge", "sweeping_edge"),
            Map.entry("depth_strider", "depth_strider"),
            Map.entry("frost_walker", "frost_walker"),
            Map.entry("loyalty", "loyalty"),
            Map.entry("impaling", "impaling"),
            Map.entry("riptide", "riptide"),
            Map.entry("channeling", "channeling"),
            Map.entry("multishot", "multishot"),
            Map.entry("piercing", "piercing"),
            Map.entry("quick_charge", "quick_charge"),
            Map.entry("soul_speed", "soul_speed"),
            Map.entry("swift_sneak", "swift_sneak"),
            Map.entry("density", "density"),
            Map.entry("breach", "breach"),
            Map.entry("wind_burst", "wind_burst"));

    @Override
    public String id() {
        return "essentials";
    }

    @Override
    public String plugin() {
        return "Essentials";
    }

    @Override
    public Imported.Result read(File pluginFolder) {
        Imported.Result result = new Imported.Result("EssentialsX");
        ConfigurationSection kits = YamlConfiguration.loadConfiguration(new File(pluginFolder, "kits.yml"))
                .getConfigurationSection("kits");
        if (kits == null) {
            kits = YamlConfiguration.loadConfiguration(new File(pluginFolder, "config.yml"))
                    .getConfigurationSection("kits");
        }
        if (kits == null) {
            result.warn(Tr.t("Aucun kit trouvé dans kits.yml ni config.yml"));
            return result;
        }
        Map<String, String> aliases = aliases(new File(pluginFolder, "items.json"), result);
        Map<String, String> ids = new LinkedHashMap<>();
        for (String name : kits.getKeys(false)) {
            ConfigurationSection section = kits.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            Imported.Kit kit = kit(name, section, aliases, result);
            ids.put(name.replace('.', '_').replace('/', '_').toLowerCase(Locale.ENGLISH), kit.id());
            result.add(kit);
        }
        readClaims(new File(pluginFolder, "userdata"), ids, result);
        return result;
    }

    static Imported.Kit kit(String name, ConfigurationSection section, Map<String, String> aliases,
                            Imported.Result result) {
        long delay = section.getLong("delay", 0L);
        List<Imported.Item> items = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        double money = 0.0D;
        Set<String> skipped = new TreeSet<>();
        for (String line : section.getStringList("items")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("/")) {
                commands.add(KitSource.command(trimmed));
            } else if (trimmed.startsWith("$")) {
                try {
                    money += Double.parseDouble(trimmed.substring(1).trim());
                } catch (NumberFormatException invalid) {
                    result.warn(name + Tr.t(" : montant illisible ") + trimmed);
                }
            } else if (trimmed.startsWith("@")) {
                items.add(Imported.Item.serialized(trimmed.substring(1).replaceAll("\\s", "")));
            } else {
                Imported.Item item = item(trimmed, aliases, skipped);
                if (item == null) {
                    result.warn(name + Tr.t(" : objet inconnu ") + trimmed);
                } else {
                    items.add(item);
                }
            }
        }
        if (!skipped.isEmpty()) {
            result.warn(name + Tr.t(" : options ignorées ") + String.join(", ", skipped));
        }
        return new Imported.Kit(KitSource.slug(name), name, Math.max(0L, delay), delay < 0L, 0.0D, items, commands,
                money);
    }

    static Imported.Item item(String line, Map<String, String> aliases, Set<String> skipped) {
        String[] parts = line.split(" +");
        String token = parts[0].toLowerCase(Locale.ROOT);
        if (token.startsWith("minecraft:")) {
            token = token.substring(10);
        }
        String material = resolve(token.split(":", 2)[0], aliases);
        if (material == null) {
            return null;
        }
        int amount = 1;
        int start = 1;
        if (parts.length > 1 && parts[1].matches("\\d+")) {
            amount = Integer.parseInt(parts[1]);
            start = 2;
        }
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("material", material);
        List<String> enchantments = new ArrayList<>();
        for (int index = start; index < parts.length; index++) {
            String[] meta = parts[index].split(":", 2);
            String key = meta[0].toLowerCase(Locale.ROOT);
            String value = meta.length > 1 ? meta[1] : "";
            switch (key) {
                case "name" -> spec.put("name", ImportText.mini(value.replace('_', ' ')));
                case "lore" -> spec.put("lore", ImportText.mini(List.of(value.replace('_', ' ').split("\\|"))));
                default -> {
                    String enchantment = ENCHANTMENTS.get(key);
                    if (enchantment != null) {
                        enchantments.add(enchantment + ":" + (value.matches("\\d+") ? value : "1"));
                    } else {
                        skipped.add(key);
                    }
                }
            }
        }
        if (!enchantments.isEmpty()) {
            spec.put("enchantments", enchantments);
        }
        return Imported.Item.spec(spec, amount);
    }

    static String resolve(String token, Map<String, String> aliases) {
        String key = token.startsWith("minecraft:") ? token.substring(10) : token;
        for (int depth = 0; depth < 4 && aliases.containsKey(key); depth++) {
            String next = aliases.get(key);
            if (next.equals(key)) {
                break;
            }
            key = next;
        }
        return key.matches("[a-z0-9_]+") ? key : null;
    }

    static Map<String, String> aliases(File file, Imported.Result result) {
        Map<String, String> aliases = new HashMap<>();
        if (!file.isFile()) {
            return aliases;
        }
        try {
            String json = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.startsWith("#"))
                    .collect(Collectors.joining("\n"));
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    aliases.put(entry.getKey(), value.getAsString());
                } else if (value.isJsonObject() && value.getAsJsonObject().has("material")) {
                    aliases.put(entry.getKey(), value.getAsJsonObject().get("material").getAsString()
                            .toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException | RuntimeException failure) {
            result.warn(Tr.t("items.json illisible, seuls les noms Minecraft sont reconnus : ") + failure.getMessage());
        }
        return aliases;
    }

    private static void readClaims(File userdata, Map<String, String> ids, Imported.Result result) {
        File[] files = userdata.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            UUID player;
            try {
                player = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            ConfigurationSection stamps = YamlConfiguration.loadConfiguration(file)
                    .getConfigurationSection("timestamps.kits");
            if (stamps == null) {
                continue;
            }
            for (String kit : stamps.getKeys(false)) {
                String id = ids.get(kit.toLowerCase(Locale.ENGLISH));
                if (id != null) {
                    result.claim(player, id, stamps.getLong(kit));
                }
            }
        }
    }
}
