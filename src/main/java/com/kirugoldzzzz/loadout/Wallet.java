package com.kirugoldzzzz.loadout;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class Wallet {

    private volatile Economy economy;

    public boolean available() {
        return provider() != null;
    }

    public boolean playerOperationsAllowed() {
        return available();
    }

    public double balance(UUID player) {
        Economy current = provider();
        return current == null ? 0.0D : current.getBalance(offline(player));
    }

    public boolean deposit(UUID player, double amount) {
        Economy current = provider();
        if (current == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return false;
        }
        return current.depositPlayer(offline(player), amount).transactionSuccess();
    }

    public boolean withdraw(UUID player, double amount) {
        Economy current = provider();
        if (current == null || !Double.isFinite(amount) || amount < 0.0D) {
            return false;
        }
        if (amount == 0.0D) {
            return true;
        }
        OfflinePlayer target = offline(player);
        return current.has(target, amount) && current.withdrawPlayer(target, amount).transactionSuccess();
    }

    public void creditOwed(UUID player, double amount) {
        deposit(player, amount);
    }

    public synchronized <T> T change(Supplier<T> work) {
        return work.get();
    }

    public String nameOf(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? player.toString().substring(0, 8) : name;
    }

    public Optional<UUID> resolve(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? Optional.empty() : Optional.of(cached.getUniqueId());
    }

    public List<String> namesMatching(String prefix, int limit) {
        String lowered = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (names.size() >= limit) {
                break;
            }
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lowered)) {
                names.add(player.getName());
            }
        }
        return names;
    }

    private Economy provider() {
        Economy current = economy;
        if (current != null) {
            return current;
        }
        if (Bukkit.getServer() == null || Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            economy = registration.getProvider();
        }
        return economy;
    }

    private static OfflinePlayer offline(UUID player) {
        return Bukkit.getOfflinePlayer(player);
    }
}
