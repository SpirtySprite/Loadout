package com.kirugoldzzzz.loadout.common.item;

import com.kirugoldzzzz.loadout.common.text.Tr;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ItemCodec {

    static final String BINARY_PREFIX = "v2:";
    static final String PLAIN_PREFIX = "plain:";
    private static final int MAX_ITEMS = 250_000;

    private ItemCodec() {
    }

    public static String encodeAll(List<ItemStack> items) {
        List<ItemStack> present = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            if (item.getAmount() > item.getMaxStackSize()) {
                present.addAll(Inventories.split(item, item.getAmount()));
            } else {
                present.add(item);
            }
        }
        try {
            return BINARY_PREFIX + Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(present));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(Tr.t("Impossible d'encoder les objets"), exception);
        }
    }

    public static String encodePlain(Material material, int amount) {
        return PLAIN_PREFIX + material.name() + ":" + Math.max(0, amount);
    }

    public static int plainAmount(String encoded, Material material) {
        if (encoded == null || !encoded.startsWith(PLAIN_PREFIX)) {
            return -1;
        }
        int separator = encoded.lastIndexOf(':');
        if (separator <= PLAIN_PREFIX.length()
                || !encoded.substring(PLAIN_PREFIX.length(), separator).equals(material.name())) {
            return -1;
        }
        try {
            int amount = Integer.parseInt(encoded.substring(separator + 1));
            return amount < 0 ? -1 : amount;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public static List<ItemStack> decodeAll(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return decodeAllRequired(encoded);
        } catch (IllegalStateException exception) {
            return new ArrayList<>();
        }
    }

    public static List<ItemStack> decodeAllRequired(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new ArrayList<>();
        }
        if (encoded.startsWith(PLAIN_PREFIX)) {
            return decodePlain(encoded);
        }
        return encoded.startsWith(BINARY_PREFIX) ? decodeBinary(encoded.substring(BINARY_PREFIX.length()))
                : decodeLegacy(encoded);
    }

    private static List<ItemStack> decodePlain(String encoded) {
        int separator = encoded.lastIndexOf(':');
        Material material = separator <= PLAIN_PREFIX.length() ? null
                : Material.getMaterial(encoded.substring(PLAIN_PREFIX.length(), separator));
        int amount = material == null ? -1 : plainAmount(encoded, material);
        if (material == null || amount < 0 || !material.isItem() || material.isAir()) {
            throw new IllegalStateException(Tr.t("Objets encodés invalides"));
        }
        return Inventories.split(new ItemStack(material), amount);
    }

    private static List<ItemStack> decodeBinary(String payload) {
        try {
            ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(payload));
            if (decoded.length > MAX_ITEMS) {
                throw new IllegalStateException(Tr.t("Nombre d'objets encodés invalide"));
            }
            List<ItemStack> items = new ArrayList<>(decoded.length);
            for (ItemStack item : decoded) {
                if (item != null && !item.isEmpty()) {
                    items.add(item);
                }
            }
            return items;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(Tr.t("Impossible de décoder les objets"), exception);
        }
    }

    private static List<ItemStack> decodeLegacy(String encoded) {
        try (ByteArrayInputStream buffer = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream stream = new BukkitObjectInputStream(buffer)) {
            int size = stream.readInt();
            if (size < 0 || size > MAX_ITEMS) {
                throw new IllegalStateException(Tr.t("Nombre d'objets encodés invalide"));
            }
            List<ItemStack> items = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                Object read = stream.readObject();
                if (read instanceof ItemStack item) {
                    items.add(item);
                }
            }
            return items;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(Tr.t("Impossible de décoder les objets"), exception);
        }
    }
}
