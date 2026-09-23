package com.kirugoldzzzz.loadout.common.item;

import com.foliagui.builder.item.BaseItemBuilder;
import com.foliagui.builder.item.ItemBuilder;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class ItemSpec {

    private ItemSpec() {
    }

    public static ItemStack read(ConfigurationSection section, Material fallback) {
        if (section != null && section.isItemStack("item")) {
            ItemStack stored = section.getItemStack("item");
            if (stored != null && !stored.getType().isAir()) {
                return stored.clone();
            }
        }
        return builder(section, fallback).build();
    }

    public static void write(ConfigurationSection section, ItemStack item) {
        section.set("item", item.clone());
        section.set("material", item.getType().name());
    }

    public static BaseItemBuilder<?> builder(ConfigurationSection section, Material fallback) {
        Material material = material(section == null ? null : section.getString("material"), fallback);
        String texture = section == null ? null : section.getString("skull-texture");
        BaseItemBuilder<?> builder = texture != null
                ? ItemBuilder.skull().texture(texture)
                : ItemBuilder.of(material);

        if (section == null) {
            return builder;
        }

        builder.amount(Math.max(1, section.getInt("amount", 1)));

        String name = section.getString("name");
        if (name != null) {
            builder.name(Mini.label(name));
        }

        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            builder.loreComponents(Mini.labels(lore));
        }

        if (section.contains("custom-model-data")) {
            builder.customModelData(section.getInt("custom-model-data"));
        }

        String itemModel = section.getString("item-model");
        if (itemModel != null) {
            NamespacedKey key = NamespacedKey.fromString(itemModel);
            if (key != null) {
                builder.itemModel(key);
            }
        }

        for (String entry : section.getStringList("enchantments")) {
            applyEnchantment(builder, entry);
        }

        if (section.getBoolean("glow", false)) {
            builder.glow(true);
        }
        if (section.getBoolean("hide-extras", false)) {
            builder.hideExtras();
        }
        return builder;
    }

    private static void applyEnchantment(BaseItemBuilder<?> builder, String entry) {
        String trimmed = entry == null ? "" : entry.trim().toLowerCase(Locale.ROOT);
        String name = trimmed;
        int level = 1;
        int split = trimmed.lastIndexOf(':');
        if (split > 0) {
            int parsed = Numbers.parseInt(trimmed.substring(split + 1), -1);
            if (parsed >= 0) {
                name = trimmed.substring(0, split);
                level = parsed;
            }
        }
        NamespacedKey key = NamespacedKey.fromString(name);
        Enchantment enchantment = key == null ? null
                : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (enchantment == null || level <= 0) {
            return;
        }
        builder.enchant(enchantment, Math.min(255, level), true);
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        return material == null ? fallback : material;
    }
}
