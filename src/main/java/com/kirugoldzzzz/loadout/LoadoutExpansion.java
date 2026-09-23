package com.kirugoldzzzz.loadout;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class LoadoutExpansion extends PlaceholderExpansion {

    private final Plugin plugin;

    public LoadoutExpansion(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "loadout";
    }

    @Override
    public @NotNull String getAuthor() {
        return "KiruGoldzZz";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return KitPlaceholders.resolve(player, params);
    }
}
