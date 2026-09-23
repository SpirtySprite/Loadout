package com.kirugoldzzzz.loadout;

import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record Kit(String id, String name, List<String> description, ItemStack icon, String category, int order,
                  String permission, long cooldown, KitReset reset, LocalTime resetTime, DayOfWeek resetDay,
                  int maxUses, int stock, KitCost cost, KitRequirements requirements, Map<String, Integer> reductions,
                  KitOptions options, Map<Integer, ItemStack> contents, KitPool pool, KitRewards rewards,
                  boolean hideLocked, List<String> hint, KitMastery mastery, long lifetime, int animationLevel) {

    public static final int AUTOMATIC = -1;

    public Kit {
        description = description == null ? List.of() : List.copyOf(description);
        category = category == null || category.isBlank() ? null : category.trim().toLowerCase(Locale.ROOT);
        permission = permission == null || permission.isBlank() ? null : permission.trim();
        cooldown = Math.max(0L, cooldown);
        reset = reset == null ? KitReset.NONE : reset;
        resetTime = resetTime == null ? LocalTime.MIDNIGHT : resetTime;
        resetDay = resetDay == null ? DayOfWeek.MONDAY : resetDay;
        maxUses = Math.max(0, maxUses);
        stock = Math.max(0, stock);
        cost = cost == null ? KitCost.FREE : cost;
        requirements = requirements == null ? KitRequirements.NONE : requirements;
        reductions = reductions == null ? Map.of() : Map.copyOf(reductions);
        options = options == null ? KitOptions.DEFAULTS : options;
        TreeMap<Integer, ItemStack> ordered = new TreeMap<>();
        if (contents != null) {
            contents.forEach((slot, item) -> {
                if (KitSlots.valid(slot) && item != null) {
                    ordered.put(slot, item);
                }
            });
        }
        contents = Collections.unmodifiableMap(ordered);
        pool = pool == null ? KitPool.EMPTY : pool;
        rewards = rewards == null ? KitRewards.NONE : rewards;
        hint = hint == null ? List.of() : List.copyOf(hint);
        mastery = mastery == null ? KitMastery.NONE : mastery;
        lifetime = Math.max(0L, lifetime);
        animationLevel = animationLevel < 0 ? AUTOMATIC : Math.min(KitShow.MAXIMUM, animationLevel);
    }

    public boolean limitedUses() {
        return maxUses > 0;
    }

    public boolean limitedStock() {
        return stock > 0;
    }

    public boolean repeatable() {
        return maxUses != 1;
    }

    public boolean timed() {
        return cooldown > 0L || reset != KitReset.NONE;
    }

    public int itemCount() {
        return contents.size();
    }

    public boolean empty() {
        return contents.isEmpty() && pool.empty() && rewards.empty();
    }
}
