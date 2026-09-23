package com.kirugoldzzzz.loadout.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class KitClaimedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String kit;
    private final String source;
    private final List<ItemStack> items;
    private final int masteryLevel;

    public KitClaimedEvent(Player player, String kit, String source, List<ItemStack> items, int masteryLevel) {
        super(player, !Bukkit.isPrimaryThread());
        this.kit = kit;
        this.source = source;
        this.items = List.copyOf(items);
        this.masteryLevel = masteryLevel;
    }

    public String kit() {
        return kit;
    }

    public String source() {
        return source;
    }

    public List<ItemStack> items() {
        List<ItemStack> copies = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            copies.add(item.clone());
        }
        return copies;
    }

    public int masteryLevel() {
        return masteryLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
