package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

public record KitShow(int level, String name, Color primary, Color accent, Material pedestalMaterial, int items) {

    public static final int MAXIMUM = 4;
    public static final int RICHNESS_CAP = 3;
    public static final double RICH_VALUE = 1_000.0D;
    public static final int SOME_ITEMS = 4;
    public static final int RICH_ITEMS = 8;

    public static KitShow of(int level) {
        int safe = Math.max(0, Math.min(MAXIMUM, level));
        return switch (safe) {
            case 0 -> new KitShow(0, Tr.t("Ouverture"), Color.fromRGB(0xC9D1D9), Color.fromRGB(0xA78BFA),
                    Material.SMOOTH_STONE, 6);
            case 1 -> new KitShow(1, Tr.t("Éclat"), Color.fromRGB(0xA78BFA), Color.fromRGB(0xF0ABFC),
                    Material.AMETHYST_BLOCK, 8);
            case 2 -> new KitShow(2, Tr.t("Rituel"), Color.fromRGB(0x22D3EE), Color.fromRGB(0x67E8F9),
                    Material.SEA_LANTERN, 10);
            case 3 -> new KitShow(3, Tr.t("Tempête"), Color.fromRGB(0xFBBF24), Color.fromRGB(0xFDE68A),
                    Material.GOLD_BLOCK, 12);
            default -> new KitShow(4, Tr.t("Apothéose"), Color.fromRGB(0xF87171), Color.fromRGB(0xFBBF24),
                    Material.NETHERITE_BLOCK, 14);
        };
    }

    public static int levelFor(Kit kit, int masteryLevel) {
        if (kit.animationLevel() != Kit.AUTOMATIC) {
            return Math.max(0, Math.min(MAXIMUM, kit.animationLevel()));
        }
        return Math.min(MAXIMUM, richness(kit) + Math.max(0, masteryLevel));
    }

    public static int richness(Kit kit) {
        int richness = 0;
        if (kit.contents().size() >= SOME_ITEMS) {
            richness++;
        }
        if (kit.contents().size() >= RICH_ITEMS) {
            richness++;
        }
        if (!kit.pool().empty()) {
            richness++;
        }
        if (kit.rewards().money() >= RICH_VALUE || kit.cost().money() >= RICH_VALUE
                || kit.rewards().shards() > 0L || !kit.rewards().keys().isEmpty()) {
            richness++;
        }
        if (enchanted(kit)) {
            richness++;
        }
        return Math.min(RICHNESS_CAP, richness);
    }

    static boolean enchanted(Kit kit) {
        for (ItemStack item : kit.contents().values()) {
            if (item != null && !item.getEnchantments().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int duration() {
        return KitCeremony.of(this).duration();
    }

    public Sound ambience() {
        return switch (level) {
            case 0 -> Sound.BLOCK_CHEST_OPEN;
            case 1 -> Sound.BLOCK_AMETHYST_BLOCK_RESONATE;
            case 2 -> Sound.BLOCK_CONDUIT_ACTIVATE;
            case 3 -> Sound.ITEM_TRIDENT_THUNDER;
            default -> Sound.ENTITY_ENDER_DRAGON_GROWL;
        };
    }

    public Sound burst() {
        return switch (level) {
            case 0 -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case 1 -> Sound.BLOCK_AMETHYST_CLUSTER_BREAK;
            case 2 -> Sound.ENTITY_ILLUSIONER_CAST_SPELL;
            case 3 -> Sound.ENTITY_LIGHTNING_BOLT_IMPACT;
            default -> Sound.ITEM_TOTEM_USE;
        };
    }

    public float pitch() {
        return 1.6F - level * 0.15F;
    }
}
