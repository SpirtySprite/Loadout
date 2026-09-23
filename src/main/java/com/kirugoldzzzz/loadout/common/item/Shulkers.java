package com.kirugoldzzzz.loadout.common.item;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.function.Predicate;

public final class Shulkers {

    private Shulkers() {
    }

    public static boolean isShulker(ItemStack item) {
        return item != null && Tag.SHULKER_BOXES.isTagged(item.getType());
    }

    public static boolean isShulker(Material material) {
        return Tag.SHULKER_BOXES.isTagged(material);
    }

    public static int count(ItemStack shulker, Predicate<ItemStack> filter) {
        ShulkerBox box = boxOf(shulker);
        if (box == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack content : box.getInventory().getContents()) {
            if (content != null && !content.getType().isAir() && filter.test(content)) {
                total += content.getAmount();
            }
        }
        return total;
    }

    public static void tallyPlain(ItemStack shulker, Map<Material, Integer> tally) {
        ShulkerBox box = boxOf(shulker);
        if (box == null) {
            return;
        }
        for (ItemStack content : box.getInventory().getContents()) {
            if (content != null && !content.getType().isAir() && !content.hasItemMeta()) {
                tally.merge(content.getType(), content.getAmount(), Integer::sum);
            }
        }
    }

    public static List<ItemStack> contentsOf(ItemStack shulker) {
        ShulkerBox box = boxOf(shulker);
        if (box == null) {
            return List.of();
        }
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack content : box.getInventory().getContents()) {
            if (content != null && !content.getType().isAir()) {
                contents.add(content.clone());
            }
        }
        return contents;
    }

    public static List<ItemStack> extract(ItemStack shulker, Predicate<ItemStack> filter, int limit) {
        if (!(shulker.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return List.of();
        }
        Inventory inventory = box.getInventory();
        List<ItemStack> extracted = new ArrayList<>();
        int remaining = limit;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack content = contents[slot];
            if (content == null || content.getType().isAir() || !filter.test(content)) {
                continue;
            }
            int taken = Math.min(remaining, content.getAmount());
            ItemStack copy = content.clone();
            copy.setAmount(taken);
            extracted.add(copy);
            remaining -= taken;
            if (taken >= content.getAmount()) {
                contents[slot] = null;
            } else {
                content.setAmount(content.getAmount() - taken);
                contents[slot] = content;
            }
        }
        if (!extracted.isEmpty()) {
            inventory.setContents(contents);
            meta.setBlockState(box);
            shulker.setItemMeta(meta);
        }
        return extracted;
    }

    public static List<ItemStack> insert(ItemStack shulker, Collection<ItemStack> items) {
        if (!(shulker.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return items.stream().map(ItemStack::clone).toList();
        }
        Map<Integer, ItemStack> leftovers = box.getInventory().addItem(
                items.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        meta.setBlockState(box);
        shulker.setItemMeta(meta);
        return leftovers.values().stream().map(ItemStack::clone).toList();
    }

    private static ShulkerBox boxOf(ItemStack item) {
        if (item == null || !isShulker(item) || !(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return null;
        }
        return meta.getBlockState() instanceof ShulkerBox box ? box : null;
    }
}
