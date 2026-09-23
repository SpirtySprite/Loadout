package com.kirugoldzzzz.loadout.common.item;

import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.kirugoldzzzz.loadout.common.util.LruCache;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Heads {

    private static final int MAX_CACHED = 2048;

    private static final LruCache<UUID, ItemStack> CACHE = new LruCache<>(MAX_CACHED);

    private static final Set<UUID> RESOLVING = ConcurrentHashMap.newKeySet();

    private Heads() {
    }

    public static ItemStack of(UUID uuid) {
        ItemStack cached = CACHE.get(uuid);
        if (cached != null) {
            return cached.clone();
        }
        warm(uuid);
        return new ItemStack(Material.PLAYER_HEAD);
    }

    public static void remember(Player player) {
        CACHE.put(player.getUniqueId(), build(player));
        RESOLVING.remove(player.getUniqueId());
    }

    public static void forget(UUID uuid) {
        CACHE.remove(uuid);
        RESOLVING.remove(uuid);
    }

    public static void clear() {
        CACHE.clear();
        RESOLVING.clear();
    }

    public static int cached() {
        return CACHE.size();
    }

    public static void warm(UUID uuid) {
        if (CACHE.contains(uuid) || !RESOLVING.add(uuid)) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            remember(online);
            return;
        }
        Scheduling.async(() -> {
            try {
                CACHE.put(uuid, build(Bukkit.getOfflinePlayer(uuid)));
            } catch (RuntimeException ignored) {
            } finally {
                RESOLVING.remove(uuid);
            }
        });
    }

    private static ItemStack build(OfflinePlayer owner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
            head.setItemMeta(skull);
        }
        return head;
    }
}
