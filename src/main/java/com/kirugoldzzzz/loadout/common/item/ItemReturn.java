package com.kirugoldzzzz.loadout.common.item;

import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.PluginLog;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.storage.RecoveryRepository;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ItemReturn {

    public static final String DEFAULT_SOURCE = Tr.t("Inventaire plein");

    private static volatile RecoveryRepository recovery;
    private static volatile Overflow overflow;
    private static volatile boolean shuttingDown;

    private ItemReturn() {
    }

    public static void bind(RecoveryRepository repository) {
        recovery = repository;
        shuttingDown = false;
    }

    public static void shuttingDown() {
        shuttingDown = true;
    }

    public static void overflow(Overflow sink) {
        overflow = sink;
    }

    public static void give(Player player, Collection<ItemStack> items) {
        give(player, items, DEFAULT_SOURCE);
    }

    public static void give(Player player, Collection<ItemStack> items, String source) {
        if (items.isEmpty()) {
            return;
        }
        List<ItemStack> copies = copies(items);
        if (copies.isEmpty()) {
            return;
        }
        UUID owner = player.getUniqueId();
        if (shuttingDown || !player.isOnline()) {
            park(owner, copies);
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            deliver(player, copies, source);
            return;
        }
        if (player.getScheduler().run(Scheduling.owner(), task -> {
            if (shuttingDown || !player.isOnline()) {
                park(owner, copies);
            } else {
                deliver(player, copies, source);
            }
        }, () -> park(owner, copies)) == null) {
            park(owner, copies);
        }
    }

    private static void deliver(Player player, List<ItemStack> copies, String source) {
        List<ItemStack> leftovers = giveOrKeep(player, copies);
        if (leftovers.isEmpty()) {
            return;
        }
        Overflow sink = overflow;
        if (sink != null) {
            try {
                sink.store(player, leftovers, source == null || source.isBlank() ? DEFAULT_SOURCE : source);
                return;
            } catch (RuntimeException failure) {
                PluginLog.warn(LogTopic.STORAGE, Tr.t("Réserve indisponible pour ") + player.getName()
                        + Tr.t(", objets mis de côté jusqu'à la prochaine connexion"), failure);
            }
        }
        park(player.getUniqueId(), leftovers);
    }

    public static List<ItemStack> giveOrKeep(Player player, Collection<ItemStack> items) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                leftovers.addAll(player.getInventory().addItem(item.clone()).values());
            }
        }
        return leftovers;
    }

    public static void park(UUID owner, Collection<ItemStack> items) {
        RecoveryRepository repository = recovery;
        if (repository != null) {
            repository.store(owner, List.copyOf(items));
        }
    }

    public static List<ItemStack> claim(UUID owner) {
        RecoveryRepository repository = recovery;
        return repository == null ? List.of() : repository.take(owner);
    }

    @FunctionalInterface
    public interface Overflow {
        void store(Player player, List<ItemStack> leftovers, String source);
    }

    private static List<ItemStack> copies(Collection<ItemStack> items) {
        List<ItemStack> copies = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                copies.add(item.clone());
            }
        }
        return copies;
    }
}
