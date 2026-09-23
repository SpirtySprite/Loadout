package com.kirugoldzzzz.loadout.common.effect;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Predicate;

public final class Particles {

    private static volatile Predicate<UUID> filter = ignored -> true;

    private Particles() {
    }

    public static void filter(Predicate<UUID> predicate) {
        filter = predicate == null ? ignored -> true : predicate;
    }

    public static boolean allowed(UUID player) {
        return player != null && filter.test(player);
    }

    public static boolean wanted(Player player) {
        return player != null && player.isOnline() && allowed(player.getUniqueId());
    }

    public static void show(Player player, Particle particle, Location at, int count,
                            double spreadX, double spreadY, double spreadZ, double speed) {
        if (!wanted(player)) {
            return;
        }
        player.spawnParticle(particle, at, count, spreadX, spreadY, spreadZ, speed);
    }

    public static <T> void show(Player player, Particle particle, Location at, int count,
                                double spreadX, double spreadY, double spreadZ, double speed, T data) {
        if (!wanted(player)) {
            return;
        }
        player.spawnParticle(particle, at, count, spreadX, spreadY, spreadZ, speed, data);
    }

    public static <T> void show(Player player, Particle particle, double x, double y, double z, int count,
                                double spreadX, double spreadY, double spreadZ, double speed, T data) {
        if (!wanted(player)) {
            return;
        }
        player.spawnParticle(particle, x, y, z, count, spreadX, spreadY, spreadZ, speed, data);
    }
}
