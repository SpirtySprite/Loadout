package com.kirugoldzzzz.loadout.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitSourceTest {

    private static final UUID PLAYER = UUID.fromString("0f0e0d0c-0b0a-0908-0706-050403020100");

    @TempDir
    Path folder;

    private void write(String path, String... lines) throws IOException {
        Path target = folder.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    @Test
    void essentialsKitsWithAliasesMetaCommandsAndCooldowns() throws IOException {
        write("kits.yml",
                "kits:",
                "  dtools:",
                "    delay: 600",
                "    items:",
                "      - dpickaxe 1 efficiency:1 durability:3 name:&4Giga_drill lore:The_drill|of_heaven",
                "      - daxe:780 2",
                "      - playerhead 1 player:Notch",
                "      - /broadcast {USERNAME} got tools",
                "      - $250",
                "  starter:",
                "    delay: -1",
                "    items:",
                "      - bread 16");
        write("items.json",
                "#version: 2.21",
                "{",
                "  \"diamond_pickaxe\": {\"material\": \"DIAMOND_PICKAXE\"},",
                "  \"dpickaxe\": \"diamond_pickaxe\",",
                "  \"diamond_axe\": {\"material\": \"DIAMOND_AXE\"},",
                "  \"daxe\": \"diamond_axe\",",
                "  \"player_head\": {\"material\": \"PLAYER_HEAD\"},",
                "  \"playerhead\": \"player_head\"",
                "}");
        write("userdata/" + PLAYER + ".yml",
                "timestamps:",
                "  kits:",
                "    dtools: 1700000000000",
                "    unknown: 5");

        Imported.Result result = new EssentialsKitsSource().read(folder.toFile());

        assertEquals(2, result.kits().size());
        Imported.Kit tools = result.kits().getFirst();
        assertEquals("dtools", tools.id());
        assertEquals(600L, tools.cooldownSeconds());
        assertFalse(tools.once());
        assertEquals(3, tools.items().size());
        Imported.Item pickaxe = tools.items().getFirst();
        assertEquals("diamond_pickaxe", pickaxe.material());
        assertEquals(List.of("efficiency:1", "unbreaking:3"), pickaxe.spec().get("enchantments"));
        assertEquals(List.of("The drill", "of heaven"), pickaxe.spec().get("lore"));
        assertFalse(((String) pickaxe.spec().get("name")).contains("&"));
        assertEquals("diamond_axe", tools.items().get(1).material());
        assertEquals(2, tools.items().get(1).amount());
        assertEquals(List.of("broadcast <player> got tools"), tools.commands());
        assertEquals(250.0D, tools.money());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("player")));

        Imported.Kit starter = result.kits().get(1);
        assertTrue(starter.once());
        assertEquals(16, starter.items().getFirst().amount());

        assertEquals(Map.of("dtools", 1_700_000_000_000L), result.claims().get(PLAYER));
    }

    @Test
    void essentialsWithoutAliasFileKeepsMinecraftNames() {
        Imported.Item item = EssentialsKitsSource.item("minecraft:iron_sword 1 sharpness:2", Map.of(),
                new java.util.TreeSet<>());
        assertEquals("iron_sword", item.material());
        assertEquals(List.of("sharpness:2"), item.spec().get("enchantments"));
    }

    @Test
    void ultimateKitsItemsMoneyCommandsAndDelays() throws IOException {
        write("kit.yml",
                "Kits:",
                "  Warrior:",
                "    delay: 3600",
                "    price: 500.0",
                "    title: '&cWarrior'",
                "    items:",
                "      - '{count:1,id:\"minecraft:iron_sword\"}'",
                "      - 'chance:50~display-item:DIAMOND;{count:2,id:\"minecraft:golden_apple\"}'",
                "      - '$100'",
                "      - '/give {player} diamond 1'",
                "      - 'garbage'");
        write("data.yml",
                "Kits:",
                "  Warrior:",
                "    delays:",
                "      " + PLAYER + ": 1700000000000");

        Imported.Result result = new UltimateKitsSource().read(folder.toFile());
        Imported.Kit kit = result.kits().getFirst();

        assertEquals("warrior", kit.id());
        assertEquals(3600L, kit.cooldownSeconds());
        assertEquals(500.0D, kit.price());
        assertEquals(2, kit.items().size());
        assertEquals("minecraft:golden_apple", kit.items().get(1).argument());
        assertEquals(2, kit.items().get(1).amount());
        assertEquals(100.0D, kit.money());
        assertEquals(List.of("give <player> diamond 1"), kit.commands());
        assertEquals(1, result.warnings().size());
        assertEquals(Map.of("warrior", 1_700_000_000_000L), result.claims().get(PLAYER));
    }

    @Test
    void missingFilesAreReported() {
        assertEquals(1, new UltimateKitsSource().read(folder.toFile()).warnings().size());
        assertEquals(1, new EssentialsKitsSource().read(folder.toFile()).warnings().size());
    }
}
