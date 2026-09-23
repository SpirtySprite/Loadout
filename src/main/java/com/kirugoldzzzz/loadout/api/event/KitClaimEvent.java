package com.kirugoldzzzz.loadout.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class KitClaimEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String kit;
    private final String source;
    private final double price;
    private boolean cancelled;

    public KitClaimEvent(Player player, String kit, String source, double price) {
        super(player, !Bukkit.isPrimaryThread());
        this.kit = kit;
        this.source = source;
        this.price = price;
    }

    public String kit() {
        return kit;
    }

    public String source() {
        return source;
    }

    public double price() {
        return price;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
