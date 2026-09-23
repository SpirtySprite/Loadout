package com.kirugoldzzzz.loadout;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public record KitPool(int rolls, boolean unique, List<Entry> entries) {

    public static final int MAXIMUM_ROLLS = 27;
    static final int EXACT_LIMIT = 12;
    public static final KitPool EMPTY = new KitPool(0, true, List.of());

    public record Entry(String id, ItemStack item, int weight, int minimum, int maximum) {

        public Entry {
            weight = Math.max(1, weight);
            minimum = Math.max(1, minimum);
            maximum = Math.max(minimum, maximum);
        }
    }

    public record Pull(Entry entry, int amount) {
    }

    public KitPool {
        entries = entries == null ? List.of() : List.copyOf(entries);
        rolls = Math.max(0, Math.min(MAXIMUM_ROLLS, rolls));
    }

    public boolean empty() {
        return entries.isEmpty() || rolls <= 0;
    }

    public int totalWeight() {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.weight();
        }
        return total;
    }

    public double share(Entry entry) {
        int total = totalWeight();
        return total <= 0 ? 0.0D : entry.weight() / (double) total;
    }

    public boolean exact() {
        return !unique || entries.size() <= EXACT_LIMIT;
    }

    public double chance(Entry entry) {
        int index = entries.indexOf(entry);
        if (index < 0 || empty()) {
            return 0.0D;
        }
        int draws = effectiveRolls();
        if (!unique) {
            return 1.0D - Math.pow(1.0D - share(entry), draws);
        }
        if (draws >= entries.size()) {
            return 1.0D;
        }
        if (!exact()) {
            return share(entry);
        }
        int full = (1 << entries.size()) - 1;
        return inclusion(index, full, draws, new HashMap<>());
    }

    private double inclusion(int index, int mask, int draws, Map<Integer, Double> memo) {
        if (draws <= 0) {
            return 0.0D;
        }
        Double known = memo.get(mask);
        if (known != null) {
            return known;
        }
        int total = 0;
        for (int other = 0; other < entries.size(); other++) {
            if ((mask & (1 << other)) != 0) {
                total += entries.get(other).weight();
            }
        }
        double result = 0.0D;
        for (int other = 0; other < entries.size(); other++) {
            if ((mask & (1 << other)) == 0) {
                continue;
            }
            double pick = entries.get(other).weight() / (double) total;
            result += other == index ? pick : pick * inclusion(index, mask & ~(1 << other), draws - 1, memo);
        }
        memo.put(mask, result);
        return result;
    }

    public int effectiveRolls() {
        return unique ? Math.min(rolls, entries.size()) : rolls;
    }

    public List<Pull> roll(RandomGenerator random) {
        List<Pull> pulls = new ArrayList<>();
        if (empty()) {
            return pulls;
        }
        List<Entry> remaining = new ArrayList<>(entries);
        int draws = effectiveRolls();
        for (int draw = 0; draw < draws && !remaining.isEmpty(); draw++) {
            int total = 0;
            for (Entry entry : remaining) {
                total += entry.weight();
            }
            int target = random.nextInt(total);
            Entry picked = remaining.getLast();
            for (Entry entry : remaining) {
                target -= entry.weight();
                if (target < 0) {
                    picked = entry;
                    break;
                }
            }
            int amount = picked.minimum() == picked.maximum() ? picked.minimum()
                    : picked.minimum() + random.nextInt(picked.maximum() - picked.minimum() + 1);
            pulls.add(new Pull(picked, amount));
            if (unique) {
                remaining.remove(picked);
            }
        }
        return pulls;
    }
}
