package com.kirugoldzzzz.loadout.common.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class Inventories {

    private Inventories() {
    }

    public static Map<Material, Integer> plainTally(Inventory inventory) {
        Map<Material, Integer> tally = new EnumMap<>(Material.class);
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!item.hasItemMeta()) {
                tally.merge(item.getType(), item.getAmount(), Integer::sum);
            } else if (Shulkers.isShulker(item)) {
                Shulkers.tallyPlain(item, tally);
            }
        }
        return tally;
    }

    public static List<ItemStack> takeAll(Inventory inventory, Predicate<ItemStack> filter) {
        List<ItemStack> taken = new ArrayList<>();
        ItemStack[] contents = inventory.getStorageContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir() || !filter.test(item)) {
                continue;
            }
            taken.add(item.clone());
            contents[slot] = null;
            changed = true;
        }
        if (changed) {
            inventory.setStorageContents(contents);
        }
        return taken;
    }

    public static void give(Player player, Collection<ItemStack> items) {
        ItemReturn.give(player, items);
    }

    public static void give(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemReturn.give(player, List.of(item));
    }

    public static int freeSpaceFor(PlayerInventory inventory, ItemStack template) {
        int maxStack = template.getMaxStackSize();
        int space = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                space += maxStack;
            } else if (item.isSimilar(template)) {
                space += Math.max(0, maxStack - item.getAmount());
            }
        }
        return space;
    }

    public static List<ItemStack> split(ItemStack template, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int size = Math.min(maxStack, remaining);
            ItemStack copy = template.clone();
            copy.setAmount(size);
            stacks.add(copy);
            remaining -= size;
        }
        return stacks;
    }
}
