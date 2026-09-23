package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.item.ItemSpec;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class KitLoader {

    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private KitLoader() {
    }

    public static KitCatalog read(ConfigurationSection root) {
        return read(root, KitLoader::item);
    }

    static KitCatalog read(ConfigurationSection root, Function<ConfigurationSection, ItemStack> items) {
        if (root == null) {
            return KitCatalog.EMPTY;
        }
        List<String> problems = new ArrayList<>();
        KitSettings settings = settings(root.getConfigurationSection("settings"), problems);
        Map<String, KitCategory> categories = new LinkedHashMap<>();
        ConfigurationSection categorySection = root.getConfigurationSection("categories");
        if (categorySection != null) {
            for (String key : categorySection.getKeys(false)) {
                ConfigurationSection section = categorySection.getConfigurationSection(key);
                String id = key.toLowerCase(Locale.ROOT);
                if (section == null || !ID.matcher(id).matches() || id.equals(KitCategory.ALL)) {
                    problems.add("Catégorie ignorée : " + key);
                    continue;
                }
                categories.put(id, new KitCategory(id, section.getString("name", key),
                        material(section.getString("icon"), Material.CHEST), section.getInt("order", categories.size()),
                        section.getStringList("description")));
            }
        }
        Map<String, Kit> kits = new LinkedHashMap<>();
        ConfigurationSection kitSection = root.getConfigurationSection("kits");
        if (kitSection != null) {
            for (String key : kitSection.getKeys(false)) {
                ConfigurationSection section = kitSection.getConfigurationSection(key);
                String id = key.toLowerCase(Locale.ROOT);
                if (section == null || !ID.matcher(id).matches()) {
                    problems.add("Kit ignoré, identifiant invalide : " + key);
                    continue;
                }
                try {
                    Kit kit = kit(id, section, settings.zone(), items, problems);
                    if (kit.category() != null && !categories.containsKey(kit.category())) {
                        problems.add("Kit " + id + " : catégorie inconnue " + kit.category());
                    }
                    kits.put(id, kit);
                } catch (RuntimeException failure) {
                    problems.add("Kit ignoré, lecture impossible : " + key + " (" + failure.getMessage() + ")");
                }
            }
        }
        KitProgression progression = progression(root.getConfigurationSection("progression"), items, problems,
                kits.keySet());
        return new KitCatalog(kits, categories, settings, problems, progression);
    }

    static KitProgression progression(ConfigurationSection section, Function<ConfigurationSection, ItemStack> items,
                                      List<String> problems, Set<String> kits) {
        if (section == null) {
            return KitProgression.NONE;
        }
        KitMastery mastery = mastery(section.getConfigurationSection("mastery"), "modèle", items, problems);
        ConfigurationSection streakSection = section.getConfigurationSection("streaks");
        KitProgression.Streaks streaks = KitProgression.Streaks.NONE;
        if (streakSection != null) {
            streaks = new KitProgression.Streaks(streakSection.getBoolean("enabled", true),
                    streakSection.getInt("bonus-per-claim", 5), streakSection.getInt("maximum-bonus", 50),
                    duration(streakSection.getString("warning"), "séries", "alerte", problems),
                    milestones(streakSection.getConfigurationSection("milestones"), "séries", problems));
        }
        Map<Integer, KitRewards> collection = milestones(section.getConfigurationSection("collection"), "collection",
                problems);
        ConfigurationSection featuredSection = section.getConfigurationSection("featured");
        KitProgression.Featured featured = KitProgression.Featured.NONE;
        if (featuredSection != null) {
            List<String> listed = new ArrayList<>();
            for (String kit : featuredSection.getStringList("kits")) {
                String id = kit.trim().toLowerCase(Locale.ROOT);
                if (kits.contains(id)) {
                    listed.add(id);
                } else {
                    problems.add("Kit du jour : kit inconnu " + kit);
                }
            }
            LocalTime rotation = KitSchedule.parseTime(featuredSection.getString("rotation", "00:00"));
            featured = new KitProgression.Featured(featuredSection.getBoolean("enabled", true),
                    featuredSection.getInt("discount", 25), listed, rotation);
        }
        return new KitProgression(mastery, streaks, collection, featured);
    }

    private static Map<Integer, KitRewards> milestones(ConfigurationSection section, String label,
                                                       List<String> problems) {
        Map<Integer, KitRewards> milestones = new LinkedHashMap<>();
        if (section == null) {
            return milestones;
        }
        for (String key : section.getKeys(false)) {
            int at = parseInt(key, -1);
            ConfigurationSection rewards = section.getConfigurationSection(key);
            if (at <= 0 || rewards == null) {
                problems.add("Palier de " + label + " invalide : " + key);
                continue;
            }
            milestones.put(at, rewards(rewards, label + " " + key, problems));
        }
        return milestones;
    }

    static KitSettings settings(ConfigurationSection section, List<String> problems) {
        if (section == null) {
            return KitSettings.DEFAULTS;
        }
        ZoneId zone = KitSettings.DEFAULTS.zone();
        String zoneName = section.getString("timezone", KitSettings.DEFAULT_ZONE);
        try {
            zone = ZoneId.of(zoneName);
        } catch (DateTimeException invalid) {
            problems.add("Fuseau horaire inconnu : " + zoneName);
        }
        ConfigurationSection voucher = section.getConfigurationSection("voucher");
        List<String> lore = voucher == null || !voucher.contains("lore") ? KitSettings.DEFAULT_VOUCHER_LORE
                : voucher.getStringList("lore");
        return new KitSettings(zone,
                section.getBoolean("show-locked", true),
                section.getBoolean("reminders", true),
                section.getBoolean("join-summary", true),
                section.getBoolean("animation", true),
                section.getBoolean("broadcasts", true),
                section.getString("protected-lore", KitSettings.DEFAULT_PROTECTED_LORE),
                material(voucher == null ? null : voucher.getString("material"), Material.PAPER),
                voucher == null ? KitSettings.DEFAULT_VOUCHER_NAME
                        : voucher.getString("name", KitSettings.DEFAULT_VOUCHER_NAME),
                lore,
                Math.max(1L, section.getLong("first-join-delay-ticks", 60L)),
                Math.max(0L, section.getLong("claim-spacing-millis", 750L)));
    }

    private static Kit kit(String id, ConfigurationSection section, ZoneId zone,
                           Function<ConfigurationSection, ItemStack> items, List<String> problems) {
        long cooldown = duration(section.getString("cooldown"), id, "recharge", problems);
        KitReset reset = KitReset.parse(section.getString("reset"));
        LocalTime resetTime = KitSchedule.parseTime(section.getString("reset-time", "00:00"));
        DayOfWeek resetDay = KitSchedule.parseDay(section.getString("reset-day", "lundi"));

        ConfigurationSection costSection = section.getConfigurationSection("cost");
        KitCost cost = costSection == null ? KitCost.FREE : new KitCost(costSection.getDouble("money", 0.0D),
                costSection.getLong("shards", 0L), costSection.getInt("levels", 0));

        Map<String, Integer> reductions = reductions(section.getStringList("cooldown-reductions"), id, problems);

        Map<Integer, ItemStack> contents = new HashMap<>();
        ConfigurationSection contentSection = section.getConfigurationSection("contents");
        if (contentSection != null) {
            for (String key : contentSection.getKeys(false)) {
                int slot = parseInt(key, -1);
                ConfigurationSection itemSection = contentSection.getConfigurationSection(key);
                if (!KitSlots.valid(slot) || itemSection == null) {
                    problems.add("Kit " + id + " : emplacement invalide " + key);
                    continue;
                }
                ItemStack item = items.apply(itemSection);
                if (item != null) {
                    contents.put(slot, item);
                }
            }
        }

        ConfigurationSection iconSection = section.getConfigurationSection("icon");
        ItemStack icon = iconSection == null ? null : items.apply(iconSection);

        return new Kit(id,
                section.getString("name", id),
                section.getStringList("description"),
                icon,
                section.getString("category"),
                section.getInt("order", 0),
                section.getString("permission"),
                cooldown,
                reset,
                resetTime,
                resetDay,
                section.getInt("max-uses", 0),
                section.getInt("stock", 0),
                cost,
                requirements(section.getConfigurationSection("requirements"), id, zone, problems),
                reductions,
                options(section.getConfigurationSection("options")),
                contents,
                pool(section.getConfigurationSection("pool"), items),
                rewards(section.getConfigurationSection("rewards"), id, problems),
                section.getBoolean("hide-locked", false),
                section.getStringList("hint"),
                mastery(section.getConfigurationSection("mastery"), id, items, problems),
                duration(section.getString("item-lifetime"), id, "durée de vie des objets", problems),
                animationLevel(section, id, problems));
    }

    private static KitRequirements requirements(ConfigurationSection section, String id, ZoneId zone,
                                                List<String> problems) {
        if (section == null) {
            return KitRequirements.NONE;
        }
        long from = KitSchedule.parseDate(section.getString("from"), zone);
        long until = KitSchedule.parseDate(section.getString("until"), zone);
        if (from < 0L || until < 0L) {
            problems.add("Kit " + id + " : date invalide, format jj/mm/aaaa hh:mm");
        }
        LocalTime[] hours = KitSchedule.parseHours(section.getString("hours"));
        if (hours == null && !section.getString("hours", "").isBlank()) {
            problems.add("Kit " + id + " : plage horaire invalide, format 18:00-23:00");
        }
        KitSchedule schedule = new KitSchedule(Math.max(0L, from), Math.max(0L, until),
                KitSchedule.parseDays(section.getStringList("days")),
                hours == null ? null : hours[0], hours == null ? null : hours[1]);
        List<KitStatistic> statistics = new ArrayList<>();
        for (String raw : section.getStringList("statistics")) {
            KitStatistic statistic = KitStatistic.parse(raw);
            if (statistic == null) {
                problems.add("Kit " + id + " : statistique invalide " + raw + ", format mob_kills:100");
            } else {
                statistics.add(statistic);
            }
        }
        return new KitRequirements(duration(section.getString("playtime"), id, "temps de jeu", problems),
                section.getStringList("kits"),
                Set.copyOf(section.getStringList("worlds")),
                section.getDouble("balance", 0.0D),
                section.getInt("level", 0),
                schedule,
                statistics);
    }

    static Map<String, Integer> reductions(List<String> lines, String id, List<String> problems) {
        Map<String, Integer> reductions = new LinkedHashMap<>();
        for (String line : lines) {
            int split = line == null ? -1 : line.lastIndexOf(':');
            int percent = split <= 0 ? -1 : parseInt(line.substring(split + 1).replace("%", ""), -1);
            if (percent <= 0) {
                problems.add("Kit " + id + " : réduction invalide " + line + ", format permission:pourcentage");
                continue;
            }
            reductions.put(line.substring(0, split).trim(), Math.min(100, percent));
        }
        return reductions;
    }

    static int animationLevel(ConfigurationSection section, String id, List<String> problems) {
        if (!section.contains("animation-level")) {
            return Kit.AUTOMATIC;
        }
        String raw = section.getString("animation-level", "auto").trim();
        if (raw.equalsIgnoreCase("auto") || raw.isEmpty()) {
            return Kit.AUTOMATIC;
        }
        int level = parseInt(raw, Integer.MIN_VALUE);
        if (level < 0 || level > KitShow.MAXIMUM) {
            problems.add("Kit " + id + " : niveau d'animation invalide " + raw + ", attendu auto ou 0 à "
                    + KitShow.MAXIMUM);
            return Kit.AUTOMATIC;
        }
        return level;
    }

    static KitMastery mastery(ConfigurationSection section, String id,
                              Function<ConfigurationSection, ItemStack> items, List<String> problems) {
        if (section == null) {
            return KitMastery.NONE;
        }
        List<KitMastery.Tier> tiers = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection tier = section.getConfigurationSection(key);
            if (tier == null) {
                continue;
            }
            if (tier.getInt("claims", 0) <= 0) {
                problems.add("Maîtrise " + id + " : palier " + key + " sans nombre de récupérations");
                continue;
            }
            List<ItemStack> bonusItems = new ArrayList<>();
            ConfigurationSection itemSection = tier.getConfigurationSection("items");
            if (itemSection != null) {
                for (String itemKey : itemSection.getKeys(false)) {
                    ConfigurationSection entry = itemSection.getConfigurationSection(itemKey);
                    ItemStack item = entry == null ? null : items.apply(entry);
                    if (item != null) {
                        bonusItems.add(item);
                    }
                }
            }
            ConfigurationSection upgrade = tier.getConfigurationSection("upgrade");
            tiers.add(new KitMastery.Tier(tier.getString("name", key), tier.getInt("claims"),
                    upgrade == null ? KitCost.FREE : new KitCost(upgrade.getDouble("money", 0.0D),
                            upgrade.getLong("shards", 0L), upgrade.getInt("levels", 0)),
                    tier.getInt("cooldown-reduction", 0), tier.getInt("bonus", 0), tier.getInt("extra-rolls", 0),
                    bonusItems));
        }
        return new KitMastery(tiers);
    }

    static KitOptions options(ConfigurationSection section) {
        if (section == null) {
            return KitOptions.DEFAULTS;
        }
        return new KitOptions(
                flag(section, KitOptions.Flag.AUTO_EQUIP),
                flag(section, KitOptions.Flag.PROTECT_ITEMS),
                flag(section, KitOptions.Flag.ANNOUNCE),
                flag(section, KitOptions.Flag.ANIMATION),
                flag(section, KitOptions.Flag.FIRST_JOIN),
                flag(section, KitOptions.Flag.RESPAWN),
                flag(section, KitOptions.Flag.GIFTABLE),
                flag(section, KitOptions.Flag.VOUCHER),
                flag(section, KitOptions.Flag.VOUCHER_IGNORES_LIMITS),
                flag(section, KitOptions.Flag.CONFIRM),
                flag(section, KitOptions.Flag.ROULETTE),
                flag(section, KitOptions.Flag.TEAM),
                flag(section, KitOptions.Flag.STREAKS),
                flag(section, KitOptions.Flag.MASTERY));
    }

    private static boolean flag(ConfigurationSection section, KitOptions.Flag flag) {
        return section.getBoolean(flag.key(), KitOptions.fallback(flag));
    }

    private static KitPool pool(ConfigurationSection section, Function<ConfigurationSection, ItemStack> items) {
        if (section == null) {
            return KitPool.EMPTY;
        }
        List<KitPool.Entry> entries = new ArrayList<>();
        ConfigurationSection entrySection = section.getConfigurationSection("entries");
        if (entrySection != null) {
            for (String key : entrySection.getKeys(false)) {
                ConfigurationSection entry = entrySection.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                ItemStack item = items.apply(entry);
                int minimum = entry.getInt("min-amount", item == null ? 1 : item.getAmount());
                entries.add(new KitPool.Entry(key, item, entry.getInt("weight", 10), minimum,
                        entry.getInt("max-amount", minimum)));
            }
        }
        return new KitPool(section.getInt("rolls", 1), section.getBoolean("unique", true), entries);
    }

    private static KitRewards rewards(ConfigurationSection section, String id, List<String> problems) {
        if (section == null) {
            return KitRewards.NONE;
        }
        Map<String, Integer> keys = new LinkedHashMap<>();
        ConfigurationSection keySection = section.getConfigurationSection("keys");
        if (keySection != null) {
            for (String crate : keySection.getKeys(false)) {
                keys.put(crate, keySection.getInt(crate));
            }
        }
        List<KitDispatch> commands = section.getStringList("commands").stream().map(KitDispatch::parse).toList();
        List<KitEffect> effects = new ArrayList<>();
        for (String raw : section.getStringList("effects")) {
            KitEffect effect = KitEffect.parse(raw);
            if (effect == null) {
                problems.add("Kit " + id + " : effet invalide " + raw);
            } else {
                effects.add(effect);
            }
        }
        return new KitRewards(section.getDouble("money", 0.0D), section.getLong("shards", 0L),
                section.getInt("levels", 0), keys, commands, effects, section.getStringList("messages"),
                section.getStringList("lines"));
    }

    private static long duration(String raw, String id, String label, List<String> problems) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        OptionalLong parsed = KitDurations.parse(raw);
        if (parsed.isEmpty()) {
            problems.add("Kit " + id + " : " + label + " invalide " + raw);
            return 0L;
        }
        return parsed.getAsLong();
    }

    static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        return material == null ? fallback : material;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static ItemStack item(ConfigurationSection section) {
        try {
            ItemStack item = ItemSpec.read(section, Material.CHEST);
            return item == null || item.getType().isAir() ? null : item;
        } catch (RuntimeException failure) {
            return null;
        }
    }
}
