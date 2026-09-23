package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.config.ConfigFile;
import com.kirugoldzzzz.loadout.common.config.Sections;
import com.kirugoldzzzz.loadout.common.item.ItemSpec;
import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.NexusLog;
import com.kirugoldzzzz.loadout.common.log.StaffAlert;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class KitEditor {

    public static final int LIST_LIMIT = 54;

    private final Supplier<YamlConfiguration> config;
    private final Supplier<Boolean> saver;
    private final Consumer<YamlConfiguration> reload;
    private final Runnable disk;

    public KitEditor(ConfigFile file, KitService service) {
        this(file::get, file::save, service::configure, file::load);
    }

    KitEditor(Supplier<YamlConfiguration> config, Supplier<Boolean> saver, Consumer<YamlConfiguration> reload) {
        this(config, saver, reload, () -> {
        });
    }

    KitEditor(Supplier<YamlConfiguration> config, Supplier<Boolean> saver, Consumer<YamlConfiguration> reload,
              Runnable disk) {
        this.config = config;
        this.saver = saver;
        this.reload = reload;
        this.disk = disk;
    }

    public static String masteryRoot(String kit) {
        return kit == null ? "progression.mastery" : "kits." + normalize(kit) + ".mastery";
    }

    public void setPath(String path, Object value, String change) {
        config.get().set(path, value);
        apply(null, change);
    }

    public ConfigurationSection section(String path) {
        return config.get().getConfigurationSection(path);
    }

    public String addTier(String root, String name, int claims) {
        String key = uniqueId(root, name);
        ConfigurationSection section = child(config.get(), root).createSection(key);
        section.set("name", name);
        section.set("claims", Math.max(1, claims));
        apply(null, "palier de maîtrise " + key);
        return key;
    }

    public void setTierValue(String root, String key, String field, Object value) {
        child(child(config.get(), root), key).set(field, value);
        apply(null, "palier " + key + " " + field);
    }

    public void removeTier(String root, String key) {
        child(config.get(), root).set(key, null);
        if (child(config.get(), root).getKeys(false).isEmpty()) {
            config.get().set(root, null);
        }
        apply(null, "retrait du palier " + key);
    }

    public void addTierItem(String root, String key, ItemStack item) {
        ConfigurationSection items = child(child(child(config.get(), root), key), "items");
        String entry = uniqueId(root + "." + key + ".items", item.getType().name().toLowerCase(Locale.ROOT));
        ItemSpec.write(items.createSection(entry), item);
        apply(null, "objet bonus du palier " + key);
    }

    public void clearTierItems(String root, String key) {
        child(child(config.get(), root), key).set("items", null);
        apply(null, "objets bonus du palier " + key);
    }

    public void copyTemplate(String kit) {
        ConfigurationSection template = config.get().getConfigurationSection(masteryRoot(null));
        String target = masteryRoot(kit);
        config.get().set(target, null);
        if (template != null) {
            Sections.copy(template, config.get().createSection(target));
        }
        apply(kit, "maîtrise personnalisée");
    }

    public void clearMastery(String kit) {
        config.get().set(masteryRoot(kit), null);
        apply(kit, "maîtrise par défaut");
    }

    public String addMilestone(String root, int count) {
        String key = String.valueOf(Math.max(1, count));
        ConfigurationSection section = child(child(config.get(), root), key);
        if (!section.contains("money")) {
            section.set("money", 0);
        }
        apply(null, "palier " + root + " " + key);
        return key;
    }

    public void setAnimationLevel(String id, int level) {
        set(id, "animation-level", level < 0 ? null : level, "niveau d'animation");
    }

    public void setLifetime(String id, long millis) {
        set(id, "item-lifetime", millis <= 0L ? null : KitDurations.write(millis), "durée de vie des objets");
    }

    public void setStatistics(String id, List<String> lines) {
        set(id, "requirements.statistics", listOrNull(lines), "défis");
    }

    public boolean exists(String id) {
        return kits().isConfigurationSection(normalize(id));
    }

    public String create(String requested, ItemStack icon) {
        String id = uniqueId("kits", requested == null || requested.isBlank() ? "kit" : requested);
        ConfigurationSection section = kits().createSection(id);
        String title = Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
        section.set("name", "<#A78BFA><b>Kit " + title + "</b>");
        section.set("description", new ArrayList<>(List.of("Un nouveau kit à personnaliser.")));
        section.set("order", kits().getKeys(false).size());
        section.set("cooldown", "1j");
        if (icon != null && !icon.getType().isAir()) {
            ItemStack single = icon.clone();
            single.setAmount(1);
            ItemSpec.write(section.createSection("icon"), single);
        } else {
            section.set("icon.material", Material.CHEST.name());
        }
        apply(id, "création");
        return id;
    }

    public String duplicate(String id) {
        ConfigurationSection source = kit(id);
        String copy = uniqueId("kits", normalize(id) + "_copie");
        ConfigurationSection target = kits().createSection(copy);
        Sections.copy(source, target);
        target.set("name", source.getString("name", id) + " <#6E7681>(copie)");
        apply(copy, "copie de " + id);
        return copy;
    }

    public void delete(String id) {
        kits().set(normalize(id), null);
        apply(null, "suppression de " + id);
    }

    public void setName(String id, String name) {
        set(id, "name", blankToNull(name), "nom");
    }

    public void setDescription(String id, List<String> lines) {
        set(id, "description", listOrNull(lines), "description");
    }

    public void setHint(String id, List<String> lines) {
        set(id, "hint", listOrNull(lines), "indice de déblocage");
    }

    public void setIcon(String id, ItemStack icon) {
        ConfigurationSection section = kit(id);
        section.set("icon", null);
        ItemStack single = icon.clone();
        single.setAmount(1);
        ItemSpec.write(section.createSection("icon"), single);
        apply(id, "icône");
    }

    public void setCategory(String id, String category) {
        set(id, "category", blankToNull(category), "catégorie");
    }

    public void setOrder(String id, int order) {
        set(id, "order", order, "ordre");
    }

    public void setPermission(String id, String permission) {
        set(id, "permission", blankToNull(permission), "permission");
    }

    public void setHideLocked(String id, boolean hide) {
        set(id, "hide-locked", hide ? Boolean.TRUE : null, "masquage");
    }

    public void setCooldown(String id, long millis) {
        set(id, "cooldown", millis <= 0L ? null : KitDurations.write(millis), "recharge");
    }

    public void setReset(String id, KitReset reset) {
        set(id, "reset", reset == KitReset.NONE ? null : reset.id(), "réinitialisation");
    }

    public void setResetTime(String id, LocalTime time) {
        set(id, "reset-time", time == null ? null : KitSchedule.TIME.format(time), "heure de réinitialisation");
    }

    public void setResetDay(String id, DayOfWeek day) {
        set(id, "reset-day", day == null ? null : KitSchedule.dayName(day), "jour de réinitialisation");
    }

    public void setMaxUses(String id, int uses) {
        set(id, "max-uses", uses <= 0 ? null : uses, "utilisations maximum");
    }

    public void setStock(String id, int stock) {
        set(id, "stock", stock <= 0 ? null : stock, "stock");
    }

    public void setCostMoney(String id, double money) {
        set(id, "cost.money", money <= 0.0D ? null : Numbers.round(money), "prix en argent");
    }

    public void setCostShards(String id, long shards) {
        set(id, "cost.shards", shards <= 0L ? null : shards, "prix en fragments");
    }

    public void setCostLevels(String id, int levels) {
        set(id, "cost.levels", levels <= 0 ? null : levels, "prix en niveaux");
    }

    public void setPlaytime(String id, long millis) {
        set(id, "requirements.playtime", millis <= 0L ? null : KitDurations.write(millis), "temps de jeu requis");
    }

    public void setRequiredKits(String id, List<String> kits) {
        set(id, "requirements.kits", listOrNull(kits), "kits requis");
    }

    public void setWorlds(String id, List<String> worlds) {
        set(id, "requirements.worlds", listOrNull(worlds), "mondes");
    }

    public void setMinimumBalance(String id, double balance) {
        set(id, "requirements.balance", balance <= 0.0D ? null : Numbers.round(balance), "solde minimum");
    }

    public void setMinimumLevel(String id, int level) {
        set(id, "requirements.level", level <= 0 ? null : level, "niveau minimum");
    }

    public void setFrom(String id, long at, ZoneId zone) {
        set(id, "requirements.from", at <= 0L ? null : KitSchedule.writeDate(at, zone), "date d'ouverture");
    }

    public void setUntil(String id, long at, ZoneId zone) {
        set(id, "requirements.until", at <= 0L ? null : KitSchedule.writeDate(at, zone), "date de fermeture");
    }

    public void setDays(String id, Set<DayOfWeek> days) {
        List<String> names = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (days.contains(day)) {
                names.add(KitSchedule.dayName(day));
            }
        }
        set(id, "requirements.days", names.isEmpty() ? null : names, "jours d'ouverture");
    }

    public void setHours(String id, LocalTime opens, LocalTime closes) {
        String value = opens == null || closes == null ? null
                : KitSchedule.TIME.format(opens) + "-" + KitSchedule.TIME.format(closes);
        set(id, "requirements.hours", value, "plage horaire");
    }

    public void setReductions(String id, Map<String, Integer> reductions) {
        List<String> lines = new ArrayList<>();
        reductions.forEach((permission, percent) -> {
            if (permission != null && !permission.isBlank() && percent != null && percent > 0) {
                lines.add(permission.trim() + ":" + Math.min(100, percent));
            }
        });
        set(id, "cooldown-reductions", lines.isEmpty() ? null : lines, "réductions de recharge");
    }

    public void setOption(String id, KitOptions.Flag flag, boolean value) {
        set(id, "options." + flag.key(), value == KitOptions.fallback(flag) ? null : value, flag.label());
    }

    public void setContents(String id, Map<Integer, ItemStack> contents) {
        ConfigurationSection section = kit(id);
        section.set("contents", null);
        if (!contents.isEmpty()) {
            ConfigurationSection target = section.createSection("contents");
            contents.forEach((slot, item) -> {
                if (KitSlots.valid(slot) && item != null && !item.getType().isAir()) {
                    ItemSpec.write(target.createSection(String.valueOf(slot)), item);
                }
            });
        }
        apply(id, "contenu");
    }

    public void setPoolRolls(String id, int rolls) {
        set(id, "pool.rolls", Math.max(0, Math.min(KitPool.MAXIMUM_ROLLS, rolls)), "tirages aléatoires");
    }

    public void setPoolUnique(String id, boolean unique) {
        set(id, "pool.unique", unique ? null : Boolean.FALSE, "tirages sans doublon");
    }

    public String addPoolEntry(String id, ItemStack item) {
        String entry = uniqueId("kits." + normalize(id) + ".pool.entries",
                item.getType().name().toLowerCase(Locale.ROOT));
        ConfigurationSection section = child(child(kit(id), "pool"), "entries").createSection(entry);
        ItemSpec.write(section, item);
        section.set("weight", 10);
        section.set("min-amount", Math.max(1, item.getAmount()));
        section.set("max-amount", Math.max(1, item.getAmount()));
        if (!kit(id).isSet("pool.rolls")) {
            kit(id).set("pool.rolls", 1);
        }
        apply(id, "entrée aléatoire " + entry);
        return entry;
    }

    public void setPoolWeight(String id, String entry, int weight) {
        set(id, "pool.entries." + entry + ".weight", Math.max(1, weight), "poids de " + entry);
    }

    public void setPoolAmounts(String id, String entry, int minimum, int maximum) {
        ConfigurationSection section = child(kit(id), "pool.entries." + entry);
        int low = Math.max(1, minimum);
        section.set("min-amount", low);
        section.set("max-amount", Math.max(low, maximum));
        apply(id, "quantités de " + entry);
    }

    public void removePoolEntry(String id, String entry) {
        set(id, "pool.entries." + entry, null, "retrait de " + entry);
    }

    public void setRewardMoney(String id, double money) {
        set(id, "rewards.money", money <= 0.0D ? null : Numbers.round(money), "argent offert");
    }

    public void setRewardShards(String id, long shards) {
        set(id, "rewards.shards", shards <= 0L ? null : shards, "fragments offerts");
    }

    public void setRewardLevels(String id, int levels) {
        set(id, "rewards.levels", levels <= 0 ? null : levels, "niveaux offerts");
    }

    public void setRewardKeys(String id, String crate, int amount) {
        ConfigurationSection section = child(child(kit(id), "rewards"), "keys");
        section.set(crate.toLowerCase(Locale.ROOT), amount <= 0 ? null : amount);
        if (section.getKeys(false).isEmpty()) {
            kit(id).set("rewards.keys", null);
        }
        apply(id, "clés offertes " + crate);
    }

    public void setRewardList(String id, String list, List<String> values) {
        set(id, "rewards." + list, listOrNull(values), "liste " + list);
    }

    public void setSetting(String key, Object value) {
        child(config.get(), "settings").set(key, value);
        apply(null, "réglage " + key);
    }

    public String createCategory(String requested, Material icon) {
        String id = uniqueId("categories", requested == null || requested.isBlank() ? "categorie" : requested);
        if (id.equals(KitCategory.ALL)) {
            id = uniqueId("categories", "categorie");
        }
        ConfigurationSection section = categories().createSection(id);
        section.set("name", "<#22D3EE><b>" + Character.toUpperCase(id.charAt(0)) + id.substring(1) + "</b>");
        section.set("icon", (icon == null ? Material.CHEST : icon).name());
        section.set("order", categories().getKeys(false).size());
        apply(null, "catégorie " + id);
        return id;
    }

    public void setCategoryName(String id, String name) {
        child(categories(), normalize(id)).set("name", blankToNull(name));
        apply(null, "nom de catégorie " + id);
    }

    public void setCategoryIcon(String id, Material icon) {
        child(categories(), normalize(id)).set("icon", icon.name());
        apply(null, "icône de catégorie " + id);
    }

    public void setCategoryOrder(String id, int order) {
        child(categories(), normalize(id)).set("order", order);
        apply(null, "ordre de catégorie " + id);
    }

    public void setCategoryDescription(String id, List<String> lines) {
        child(categories(), normalize(id)).set("description", listOrNull(lines));
        apply(null, "description de catégorie " + id);
    }

    public int deleteCategory(String id) {
        String category = normalize(id);
        categories().set(category, null);
        int moved = 0;
        for (String kit : kits().getKeys(false)) {
            ConfigurationSection section = kits().getConfigurationSection(kit);
            if (section != null && category.equalsIgnoreCase(section.getString("category"))) {
                section.set("category", null);
                moved++;
            }
        }
        apply(null, "suppression de la catégorie " + id);
        return moved;
    }

    public void reload() {
        disk.run();
        reload.accept(config.get());
    }

    private void set(String id, String path, Object value, String change) {
        kit(id).set(path, value);
        prune(kit(id));
        apply(id, change);
    }

    private static void prune(ConfigurationSection section) {
        for (String key : List.copyOf(section.getKeys(false))) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null || key.equals("icon")) {
                continue;
            }
            prune(child);
            if (child.getKeys(false).isEmpty()) {
                section.set(key, null);
            }
        }
    }

    private boolean apply(String id, String change) {
        boolean saved = saver.get();
        reload.accept(config.get());
        if (!saved && change != null) {
            NexusLog.warn(LogTopic.KITS, "kits.yml n'a pas pu être enregistré : " + change);
            StaffAlert.warning(LogTopic.KITS, "Configuration des kits non enregistrée")
                    .summary("kits.yml n'a pas pu être écrit, le changement sera perdu au redémarrage")
                    .detail(Card.CATEGORY, "Kit", id == null ? "Général" : id)
                    .detail(Card.SEARCH, "Changement", change)
                    .send();
        }
        return saved;
    }

    private ConfigurationSection kits() {
        return child(config.get(), "kits");
    }

    private ConfigurationSection categories() {
        return child(config.get(), "categories");
    }

    private ConfigurationSection kit(String id) {
        return child(kits(), normalize(id));
    }

    private static ConfigurationSection child(ConfigurationSection parent, String path) {
        ConfigurationSection existing = parent.getConfigurationSection(path);
        return existing == null ? parent.createSection(path) : existing;
    }

    private static List<String> listOrNull(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> kept = values.stream().filter(value -> value != null && !value.isBlank()).limit(LIST_LIMIT)
                .toList();
        return kept.isEmpty() ? null : new ArrayList<>(kept);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    static String slug(String raw) {
        String plain = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = plain.toLowerCase(Locale.ROOT).trim().replace(' ', '_').replaceAll("[^a-z0-9_-]", "");
        if (slug.length() > 28) {
            slug = slug.substring(0, 28);
        }
        return slug;
    }

    private String uniqueId(String path, String base) {
        ConfigurationSection section = config.get().getConfigurationSection(path);
        String slug = slug(base);
        if (slug.isEmpty()) {
            slug = "kit";
        }
        if (section == null || !section.contains(slug)) {
            return slug;
        }
        int suffix = 2;
        while (section.contains(slug + "_" + suffix)) {
            suffix++;
        }
        return slug + "_" + suffix;
    }
}
