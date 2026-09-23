package com.kirugoldzzzz.loadout;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record KitMastery(List<Tier> tiers) {

    public static final KitMastery NONE = new KitMastery(List.of());
    public static final int MAXIMUM_REDUCTION = 90;

    public record Tier(String name, int claims, KitCost upgrade, int cooldownReduction, int bonus, int extraRolls,
                       List<ItemStack> items) {

        public Tier {
            name = name == null || name.isBlank() ? "Palier" : name;
            claims = Math.max(1, claims);
            upgrade = upgrade == null ? KitCost.FREE : upgrade;
            cooldownReduction = Math.max(0, Math.min(MAXIMUM_REDUCTION, cooldownReduction));
            bonus = Math.max(0, Math.min(1_000, bonus));
            extraRolls = Math.max(0, Math.min(KitPool.MAXIMUM_ROLLS, extraRolls));
            items = items == null ? List.of() : List.copyOf(items);
        }

        public boolean purchasable() {
            return !upgrade.free();
        }
    }

    public KitMastery {
        List<Tier> sorted = new ArrayList<>(tiers == null ? List.of() : tiers);
        sorted.sort(Comparator.comparingInt(Tier::claims));
        tiers = List.copyOf(sorted);
    }

    public boolean enabled() {
        return !tiers.isEmpty();
    }

    public int level(int uses, int purchased) {
        int level = 0;
        for (int index = 0; index < tiers.size(); index++) {
            if (uses >= tiers.get(index).claims()) {
                level = index + 1;
            }
        }
        return Math.max(level, Math.min(purchased, tiers.size()));
    }

    public Tier tier(int level) {
        return level <= 0 || level > tiers.size() ? null : tiers.get(level - 1);
    }

    public Tier next(int level) {
        return tier(level + 1);
    }

    public boolean maxed(int level) {
        return enabled() && level >= tiers.size();
    }

    public int reduction(int level) {
        Tier tier = tier(level);
        return tier == null ? 0 : tier.cooldownReduction();
    }

    public int bonus(int level) {
        Tier tier = tier(level);
        return tier == null ? 0 : tier.bonus();
    }

    public int extraRolls(int level) {
        Tier tier = tier(level);
        return tier == null ? 0 : tier.extraRolls();
    }

    public double progress(int uses, int level) {
        Tier next = next(level);
        if (next == null) {
            return 1.0D;
        }
        Tier current = tier(level);
        int from = current == null ? 0 : current.claims();
        int span = Math.max(1, next.claims() - from);
        return Math.max(0.0D, Math.min(1.0D, (uses - from) / (double) span));
    }
}
