package com.kirugoldzzzz.loadout.importer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnbtTest {

    @Test
    void plainItemKeepsIdAndCount() {
        Snbt.Item item = Snbt.item("{count:3,id:\"minecraft:diamond\"}");
        assertEquals("minecraft:diamond", item.id());
        assertEquals(3, item.count());
        assertEquals("minecraft:diamond", item.argument());
    }

    @Test
    void componentsBecomeItemArgumentSyntax() {
        Snbt.Item item = Snbt.item("{components:{\"minecraft:enchantments\":{levels:{\"minecraft:sharpness\":5}},"
                + "\"minecraft:custom_name\":'{\"text\":\"Blade, sharp\"}',\"!minecraft:food\":{}},count:1b,"
                + "id:\"minecraft:diamond_sword\"}");
        assertEquals(1, item.count());
        assertEquals("minecraft:diamond_sword[minecraft:enchantments={levels:{\"minecraft:sharpness\":5}},"
                + "minecraft:custom_name='{\"text\":\"Blade, sharp\"}',!minecraft:food]", item.argument());
    }

    @Test
    void bareIdsGetTheMinecraftNamespace() {
        assertEquals("minecraft:stone", Snbt.item("{id:stone}").id());
    }

    @Test
    void entriesRespectNestingAndQuotes() {
        Map<String, String> entries = Snbt.entries("{a:[1,2,{b:\"x,y\"}],'c:d':\"}\"}");
        assertEquals("[1,2,{b:\"x,y\"}]", entries.get("a"));
        assertEquals("\"}\"", entries.get("'c:d'"));
    }

    @Test
    void brokenInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Snbt.item("{count:1}"));
        assertThrows(IllegalArgumentException.class, () -> Snbt.item("{id:\"minecraft:stone\""));
        assertThrows(IllegalArgumentException.class, () -> Snbt.item("stone"));
    }
}
