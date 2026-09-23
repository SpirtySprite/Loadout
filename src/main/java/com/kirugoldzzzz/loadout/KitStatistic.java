package com.kirugoldzzzz.loadout;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.Locale;

public record KitStatistic(Statistic statistic, Material material, EntityType entity, long amount) {

    public KitStatistic {
        amount = Math.max(1L, amount);
    }

    public static KitStatistic parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("\\s*:\\s*");
        if (parts.length < 2) {
            return null;
        }
        Statistic statistic;
        try {
            statistic = Statistic.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        long amount;
        try {
            amount = Long.parseLong(parts[parts.length - 1].replace("_", ""));
        } catch (NumberFormatException invalid) {
            return null;
        }
        if (amount <= 0L) {
            return null;
        }
        Material material = null;
        EntityType entity = null;
        switch (statistic.getType()) {
            case UNTYPED -> {
                if (parts.length != 2) {
                    return null;
                }
            }
            case BLOCK, ITEM -> {
                if (parts.length != 3) {
                    return null;
                }
                material = Material.matchMaterial(parts[1]);
                if (material == null) {
                    return null;
                }
            }
            case ENTITY -> {
                if (parts.length != 3) {
                    return null;
                }
                try {
                    entity = EntityType.valueOf(parts[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException invalid) {
                    return null;
                }
            }
        }
        return new KitStatistic(statistic, material, entity, amount);
    }

    public boolean distance() {
        return statistic.name().endsWith("_ONE_CM");
    }

    public long scale(long raw) {
        return distance() ? raw / 100L : raw;
    }

    public String write() {
        String qualifier = material != null ? ":" + material.name().toLowerCase(Locale.ROOT)
                : entity != null ? ":" + entity.name().toLowerCase(Locale.ROOT) : "";
        return statistic.name().toLowerCase(Locale.ROOT) + qualifier + ":" + amount;
    }

    public String label() {
        String subject = material != null ? name(material.name()) : entity != null ? name(entity.name()) : null;
        String base = switch (statistic) {
            case MOB_KILLS -> "Monstres tués";
            case PLAYER_KILLS -> "Joueurs tués";
            case DEATHS -> "Morts";
            case MINE_BLOCK -> "Blocs minés";
            case KILL_ENTITY -> "Créatures tuées";
            case ENTITY_KILLED_BY -> "Tué par";
            case FISH_CAUGHT -> "Poissons pêchés";
            case ANIMALS_BRED -> "Animaux élevés";
            case ITEM_ENCHANTED -> "Objets enchantés";
            case RAID_WIN -> "Raids gagnés";
            case TRADED_WITH_VILLAGER -> "Échanges villageois";
            case CRAFT_ITEM -> "Objets fabriqués";
            case USE_ITEM -> "Objets utilisés";
            case BREAK_ITEM -> "Objets usés";
            case PICKUP -> "Objets ramassés";
            case JUMP -> "Sauts";
            case WALK_ONE_CM -> "Blocs marchés";
            case SPRINT_ONE_CM -> "Blocs sprintés";
            case SWIM_ONE_CM -> "Blocs nagés";
            case FLY_ONE_CM -> "Blocs volés";
            case AVIATE_ONE_CM -> "Blocs en élytres";
            case BOAT_ONE_CM -> "Blocs en bateau";
            case HORSE_ONE_CM -> "Blocs à cheval";
            case CAKE_SLICES_EATEN -> "Parts de gâteau";
            case BELL_RING -> "Cloches sonnées";
            case TARGET_HIT -> "Cibles touchées";
            case CHEST_OPENED -> "Coffres ouverts";
            case SLEEP_IN_BED -> "Nuits dormies";
            default -> name(statistic.name());
        };
        return subject == null ? base : base + " (" + subject + ")";
    }

    static String name(String constant) {
        String text = constant.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
