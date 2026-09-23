package com.kirugoldzzzz.loadout.common.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class Scheduling {

    private static Plugin plugin;

    private Scheduling() {
    }

    public static void bind(Plugin owner) {
        plugin = owner;
    }

    public static Plugin owner() {
        return plugin;
    }

    public static boolean active() {
        Plugin owner = plugin;
        return owner != null && owner.isEnabled();
    }

    public static void async(Runnable runnable) {
        if (active()) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
        }
    }

    public static void global(Runnable runnable) {
        if (active()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
        }
    }

    public static ScheduledTask globalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, task -> runnable.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static ScheduledTask asyncLater(Runnable runnable, long delayMillis) {
        return Bukkit.getAsyncScheduler().runDelayed(plugin, task -> runnable.run(),
                Math.max(1L, delayMillis), TimeUnit.MILLISECONDS);
    }

    public static ScheduledTask asyncTimer(Runnable runnable, long delaySeconds, long periodSeconds) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(),
                Math.max(1L, delaySeconds), Math.max(1L, periodSeconds), TimeUnit.SECONDS);
    }

    public static void region(Location location, Runnable runnable) {
        if (active()) {
            Bukkit.getRegionScheduler().execute(plugin, location, runnable);
        }
    }

    public static void regionLater(Location location, Runnable runnable, long delayTicks) {
        if (active()) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> runnable.run(),
                    Math.max(1L, delayTicks));
        }
    }

    public static ScheduledTask regionTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        if (!active()) {
            return null;
        }
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, task -> runnable.run(),
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static ScheduledTask entityTimer(Entity entity, Runnable runnable,
                                            long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(plugin, task -> runnable.run(), null,
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static void entity(Entity entity, Runnable runnable) {
        if (active()) {
            entity.getScheduler().run(plugin, task -> runnable.run(), null);
        }
    }

    public static void reveal(Player player, Entity target, boolean visible) {
        reveal(player, target, visible, null);
    }

    public static void reveal(Player player, Entity target, boolean visible, Runnable skipped) {
        Runnable change = () -> {
            if (!Bukkit.isOwnedByCurrentRegion(target) || !target.isValid()) {
                if (skipped != null) {
                    skipped.run();
                }
                return;
            }
            if (visible) {
                player.showEntity(plugin, target);
            } else {
                player.hideEntity(plugin, target);
            }
        };
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            change.run();
        } else {
            entity(player, change);
        }
    }

    public static void discard(Entity entity) {
        if (entity == null) {
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            if (entity.isValid()) {
                entity.remove();
            }
            return;
        }
        if (active()) {
            entity.getScheduler().run(plugin, task -> {
                if (entity.isValid()) {
                    entity.remove();
                }
            }, null);
        }
    }

    public static void entityLater(Entity entity, Runnable runnable, long delayTicks) {
        if (active()) {
            entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, Math.max(1L, delayTicks));
        }
    }
}
