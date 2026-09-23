package com.kirugoldzzzz.loadout;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitConfigTest {

    private static KitCatalog read(YamlConfiguration yaml) {
        return KitLoader.read(yaml, ignored -> null);
    }

    @Test
    void everyFieldIsReadFromYaml() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                settings:
                  timezone: UTC
                  show-locked: false
                  reminders: false
                  voucher:
                    material: NAME_TAG
                categories:
                  pvp:
                    name: Combat
                    icon: IRON_SWORD
                    order: 2
                  debut:
                    name: Débuts
                    order: 1
                kits:
                  guerrier:
                    name: Guerrier
                    category: pvp
                    permission: loadout.kit.guerrier
                    cooldown: 1j 12h
                    reset: hebdomadaire
                    reset-time: "18h"
                    reset-day: vendredi
                    max-uses: 5
                    stock: 100
                    cost:
                      money: 250.5
                      shards: 10
                      levels: 3
                    requirements:
                      playtime: 5h
                      kits: [starter]
                      worlds: [world]
                      balance: 1000
                      level: 10
                      from: 24/12/2026
                      until: 01/01/2027 12:00
                      days: [samedi, dimanche]
                      hours: 18:00-23:00
                    cooldown-reductions:
                      - loadout.kit.vip:25
                      - loadout.kit.legende:60%
                    options:
                      animation: false
                      giftable: true
                      protect-items: true
                    pool:
                      rolls: 2
                      unique: false
                      entries:
                        diamond:
                          weight: 5
                          min-amount: 2
                          max-amount: 6
                    rewards:
                      money: 100
                      shards: 4
                      levels: 2
                      keys:
                        rare: 2
                      commands:
                        - "console: say <player>"
                        - "joueur: spawn"
                      effects:
                        - speed:1m:2
                        - n'importe:quoi:zz
                      lines: [Une surprise]
                    hide-locked: true
                    hint: [Débloqué avec le grade Guerrier]
                  starter:
                    name: Débutant
                    category: debut
                  "Bad Id":
                    name: invalide
                """);
        KitCatalog catalog = read(yaml);
        assertEquals(ZoneId.of("UTC"), catalog.settings().zone());
        assertFalse(catalog.settings().showLocked());
        assertFalse(catalog.settings().reminders());
        assertEquals(Material.NAME_TAG, catalog.settings().voucherMaterial());
        assertEquals(List.of("debut", "pvp"), List.copyOf(catalog.categories().keySet()));
        assertEquals(List.of("starter", "guerrier"), List.copyOf(catalog.kits().keySet()));
        assertEquals(Material.IRON_SWORD, catalog.categories().get("pvp").icon());

        Kit kit = catalog.kit("GUERRIER").orElseThrow();
        assertEquals("loadout.kit.guerrier", kit.permission());
        assertEquals(TimeUnit.HOURS.toMillis(36), kit.cooldown());
        assertEquals(KitReset.WEEKLY, kit.reset());
        assertEquals(LocalTime.of(18, 0), kit.resetTime());
        assertEquals(DayOfWeek.FRIDAY, kit.resetDay());
        assertEquals(5, kit.maxUses());
        assertEquals(100, kit.stock());
        assertEquals(new KitCost(250.5D, 10L, 3), kit.cost());
        assertEquals(TimeUnit.HOURS.toMillis(5), kit.requirements().playtime());
        assertEquals(List.of("starter"), kit.requirements().kits());
        assertEquals(Set.of("world"), kit.requirements().worlds());
        assertEquals(10, kit.requirements().level());
        KitSchedule schedule = kit.requirements().schedule();
        assertEquals(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), schedule.days());
        assertEquals(LocalTime.of(23, 0), schedule.closes());
        assertTrue(schedule.from() > 0L && schedule.until() > schedule.from());
        assertEquals(Map.of("loadout.kit.vip", 25, "loadout.kit.legende", 60), kit.reductions());
        assertFalse(kit.options().animation());
        assertTrue(kit.options().giftable());
        assertTrue(kit.options().protectItems());
        assertTrue(kit.options().autoEquip());
        assertEquals(2, kit.pool().rolls());
        assertFalse(kit.pool().unique());
        assertEquals(6, kit.pool().entries().getFirst().maximum());
        assertEquals(Map.of("rare", 2), kit.rewards().keys());
        assertEquals(2, kit.rewards().commands().size());
        assertFalse(kit.rewards().commands().get(1).console());
        assertEquals(1, kit.rewards().effects().size());
        assertTrue(kit.hideLocked());
        assertEquals(List.of("Débloqué avec le grade Guerrier"), kit.hint());
        assertTrue(catalog.problems().stream().anyMatch(problem -> problem.contains("Bad Id")));
        assertTrue(catalog.problems().stream().anyMatch(problem -> problem.contains("effet invalide")));
        assertEquals(1, catalog.inCategory("pvp").size());
        assertEquals(2, catalog.inCategory(KitCategory.ALL).size());
        assertEquals("Débutant", catalog.nameOf("starter"));
    }

    @Test
    void editorChangesSurviveAReload() {
        YamlConfiguration yaml = new YamlConfiguration();
        AtomicReference<KitCatalog> loaded = new AtomicReference<>(KitCatalog.EMPTY);
        KitEditor editor = new KitEditor(() -> yaml, () -> true, config -> loaded.set(read(config)));

        String id = editor.create("Épique Kit", null);
        assertEquals("epique_kit", id);
        editor.setCooldown(id, TimeUnit.MINUTES.toMillis(90));
        editor.setReset(id, KitReset.DAILY);
        editor.setResetTime(id, LocalTime.of(6, 30));
        editor.setMaxUses(id, 3);
        editor.setStock(id, 50);
        editor.setCostMoney(id, 99.999D);
        editor.setCostLevels(id, 5);
        editor.setPlaytime(id, TimeUnit.HOURS.toMillis(2));
        editor.setDays(id, Set.of(DayOfWeek.MONDAY));
        editor.setHours(id, LocalTime.of(20, 0), LocalTime.of(22, 0));
        editor.setReductions(id, Map.of("loadout.kit.vip", 50));
        editor.setOption(id, KitOptions.Flag.ANNOUNCE, true);
        editor.setOption(id, KitOptions.Flag.AUTO_EQUIP, true);
        editor.setRewardMoney(id, 10.0D);
        editor.setRewardKeys(id, "Rare", 3);
        editor.setRewardList(id, "commands", new ArrayList<>(List.of("console: say hi", " ")));
        editor.setPermission(id, "loadout.kit.epique");
        String category = editor.createCategory("Événements", Material.CAKE);
        editor.setCategory(id, category);

        Kit kit = loaded.get().kit(id).orElseThrow();
        assertEquals(TimeUnit.MINUTES.toMillis(90), kit.cooldown());
        assertEquals(KitReset.DAILY, kit.reset());
        assertEquals(LocalTime.of(6, 30), kit.resetTime());
        assertEquals(3, kit.maxUses());
        assertEquals(50, kit.stock());
        assertEquals(100.0D, kit.cost().money());
        assertEquals(5, kit.cost().levels());
        assertEquals(TimeUnit.HOURS.toMillis(2), kit.requirements().playtime());
        assertEquals(LocalTime.of(20, 0), kit.requirements().schedule().opens());
        assertEquals(Map.of("loadout.kit.vip", 50), kit.reductions());
        assertTrue(kit.options().announce());
        assertEquals(Map.of("rare", 3), kit.rewards().keys());
        assertEquals(1, kit.rewards().commands().size());
        assertEquals(category, kit.category());
        assertNotNull(loaded.get().category(category).orElse(null));
        assertFalse(yaml.contains("kits." + id + ".options.auto-equip"));

        editor.setCostMoney(id, 0.0D);
        editor.setCostLevels(id, 0);
        assertFalse(yaml.contains("kits." + id + ".cost"));

        String copy = editor.duplicate(id);
        assertEquals(3, loaded.get().kit(copy).orElseThrow().maxUses());
        assertEquals(2, editor.deleteCategory(category));
        assertNull(loaded.get().kit(id).orElseThrow().category());
        editor.delete(id);
        assertTrue(loaded.get().kit(id).isEmpty());
        assertTrue(loaded.get().problems().isEmpty(), loaded.get().problems().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"kits.yml", "lang/kits_fr.yml"})
    void bundledKitsFileLoadsCleanly(String resource) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        KitCatalog catalog = read(yaml);
        assertTrue(catalog.problems().isEmpty(), catalog.problems().toString());
        assertTrue(catalog.kits().size() >= 5);
        for (Kit kit : catalog.kits().values()) {
            if (kit.category() != null) {
                assertTrue(catalog.categories().containsKey(kit.category()), kit.id());
            }
            for (String required : kit.requirements().kits()) {
                assertTrue(catalog.kits().containsKey(required), kit.id() + " requiert " + required);
            }
        }
    }
}
