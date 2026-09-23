package com.kirugoldzzzz.loadout.common.gui;

import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ChatPrompts implements Listener {

    private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);
    private static final Map<UUID, Prompt> ACTIVE = new ConcurrentHashMap<>();

    public ChatPrompts() {
    }

    public static void open(Player player, String label, Consumer<String> accepted) {
        open(player, label, accepted, () -> {});
    }

    public static void open(Player player, String label, Consumer<String> accepted, Runnable cancelled) {
        UUID id = player.getUniqueId();
        Prompt prompt = new Prompt(accepted, cancelled);
        ACTIVE.put(id, prompt);
        player.closeInventory();
        Messages.send(player, "chat-input.request", Mini.value("field", label));
        Scheduling.asyncLater(() -> expire(player, prompt), TIMEOUT_MILLIS);
    }

    public static boolean waiting(UUID player) {
        return ACTIVE.containsKey(player);
    }

    public static void cancel(UUID player) {
        ACTIVE.remove(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = ACTIVE.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Scheduling.entity(player, () -> {
            if (cancelled(typed)) {
                Messages.send(player, "chat-input.cancelled");
                prompt.cancelled().run();
                return;
            }
            prompt.accepted().accept(typed);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ACTIVE.remove(event.getPlayer().getUniqueId());
    }

    private static void expire(Player player, Prompt prompt) {
        if (!ACTIVE.remove(player.getUniqueId(), prompt)) {
            return;
        }
        Scheduling.entity(player, () -> Messages.send(player, "chat-input.expired"));
    }

    public static boolean cancelled(String input) {
        return input != null && (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("annuler"));
    }

    private record Prompt(Consumer<String> accepted, Runnable cancelled) {
    }
}
