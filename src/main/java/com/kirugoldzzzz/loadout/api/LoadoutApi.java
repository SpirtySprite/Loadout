package com.kirugoldzzzz.loadout.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadoutApi {

    static Optional<LoadoutApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(LoadoutApi.class));
    }

    List<String> kits();

    boolean exists(String kit);

    boolean available(Player player, String kit);

    long cooldownRemaining(Player player, String kit);

    int uses(UUID player, String kit);

    boolean claim(Player player, String kit);

    boolean give(Player player, String kit);

    void giveVouchers(Player player, String kit, int amount);

    int reset(UUID player, String kit);

    int resetAll(UUID player);

    void openMenu(Player player);

    void preview(Player player, String kit);
}
