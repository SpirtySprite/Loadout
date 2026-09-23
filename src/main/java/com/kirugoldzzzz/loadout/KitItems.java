package com.kirugoldzzzz.loadout;

import com.foliagui.builder.item.ItemBuilder;
import com.kirugoldzzzz.loadout.common.text.Mini;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class KitItems {

    public static final NamespacedKey KIT_ITEM = key("kit_item");
    public static final NamespacedKey VOUCHER = key("kit_voucher");
    public static final NamespacedKey EXPIRES = key("kit_expires");
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("dd/MM à HH:mm");

    private KitItems() {
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("loadout:" + value), "clé de données invalide: " + value);
    }

    public static ItemStack protect(ItemStack item, Kit kit, String loreLine) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        meta.getPersistentDataContainer().set(KIT_ITEM, PersistentDataType.STRING, kit.id());
        if (loreLine != null && !loreLine.isBlank()) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Mini.label(loreLine));
            meta.lore(lore);
        }
        copy.setItemMeta(meta);
        return copy;
    }

    public static ItemStack expiring(ItemStack item, long expiresAt, ZoneId zone) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        meta.getPersistentDataContainer().set(EXPIRES, PersistentDataType.LONG, expiresAt);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Mini.label("<#FBBF24>⌛ <#C9D1D9>Disparaît le <#FBBF24>"
                + EXPIRY.format(Instant.ofEpochMilli(expiresAt).atZone(zone))));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    public static boolean expired(ItemStack item, long now) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Long at = item.getPersistentDataContainer().get(EXPIRES, PersistentDataType.LONG);
        return at != null && at <= now;
    }

    public static int sweep(Inventory inventory, long now) {
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            if (expired(contents[slot], now)) {
                removed += contents[slot].getAmount();
                contents[slot] = null;
                changed = true;
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
        return removed;
    }

    public static boolean isProtected(ItemStack item) {
        return read(item, KIT_ITEM) != null;
    }

    public static String kitOf(ItemStack item) {
        return read(item, KIT_ITEM);
    }

    public static ItemStack voucher(Kit kit, KitSettings settings, int amount) {
        Component name = Mini.label(kit.name());
        ItemStack voucher = ItemBuilder.of(settings.voucherMaterial())
                .amount(Math.max(1, Math.min(settings.voucherMaterial().getMaxStackSize(), amount)))
                .name(Mini.label(settings.voucherName(), Mini.component("kit", name)))
                .loreComponents(settings.voucherLore().stream()
                        .map(line -> Mini.label(line, Mini.component("kit", name)))
                        .toList())
                .glow(true)
                .build();
        ItemMeta meta = voucher.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(VOUCHER, PersistentDataType.STRING, kit.id());
            voucher.setItemMeta(meta);
        }
        return voucher;
    }

    public static List<ItemStack> vouchers(Kit kit, KitSettings settings, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        int stack = Math.max(1, settings.voucherMaterial().getMaxStackSize());
        int remaining = Math.max(1, amount);
        while (remaining > 0) {
            int size = Math.min(stack, remaining);
            stacks.add(voucher(kit, settings, size));
            remaining -= size;
        }
        return stacks;
    }

    public static String voucherKit(ItemStack item) {
        String kit = read(item, VOUCHER);
        return kit == null ? null : kit.toLowerCase(Locale.ROOT);
    }

    public static boolean takeVoucher(Player player, String kit) {
        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        if (kit.equals(voucherKit(hand))) {
            if (hand.getAmount() <= 1) {
                inventory.setItemInMainHand(null);
            } else {
                hand.setAmount(hand.getAmount() - 1);
                inventory.setItemInMainHand(hand);
            }
            return true;
        }
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!kit.equals(voucherKit(item))) {
                continue;
            }
            if (item.getAmount() <= 1) {
                contents[slot] = null;
            } else {
                item.setAmount(item.getAmount() - 1);
            }
            inventory.setStorageContents(contents);
            return true;
        }
        return false;
    }

    private static String read(ItemStack item, NamespacedKey tag) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
    }
}
