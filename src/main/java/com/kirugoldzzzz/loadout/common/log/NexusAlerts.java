package com.kirugoldzzzz.loadout.common.log;

import com.kirugoldzzzz.loadout.common.text.Tr;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class NexusAlerts {

    public static final String ALL = "loadout.alerts";

    private static final int THROTTLE_LIMIT = 1_024;
    private static final long THROTTLE_MEMORY = TimeUnit.HOURS.toMillis(6L);

    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();
    private static volatile Predicate<UUID> listening = ignored -> true;

    private NexusAlerts() {
    }

    public static void listening(Predicate<UUID> filter) {
        listening = filter == null ? ignored -> true : filter;
    }

    public static boolean staff(Permissible permissible) {
        if (permissible.hasPermission(ALL)) {
            return true;
        }
        for (LogTopic topic : LogTopic.values()) {
            if (permissible.hasPermission(topic.permission())) {
                return true;
            }
        }
        return false;
    }

    public static boolean receives(Player player, LogTopic topic) {
        return player.hasPermission(topic.permission()) && listening.test(player.getUniqueId());
    }

    static boolean admit(String key, long millis, long now) {
        if (key == null || millis <= 0L) {
            return true;
        }
        if (LAST.size() > THROTTLE_LIMIT) {
            LAST.values().removeIf(last -> now - last > THROTTLE_MEMORY);
        }
        boolean[] admitted = new boolean[1];
        LAST.compute(key, (ignored, last) -> {
            if (last == null || now - last >= millis) {
                admitted[0] = true;
                return now;
            }
            return last;
        });
        return admitted[0];
    }

    static void forgetThrottles() {
        LAST.clear();
    }

    static void dispatch(StaffAlert alert) {
        NexusLog.write(alert.severity().level(), alert.topic(), alert.console(), alert.cause());
        Server server = Bukkit.getServer();
        if (server == null || !admit(alert.throttleKey(), alert.throttleMillis(), alert.at())) {
            return;
        }
        try {
            List<String> permissions = alert.permissions();
            Map<String, Component> rendered = new HashMap<>();
            for (Player player : server.getOnlinePlayers()) {
                if (!receives(player, alert.topic())) {
                    continue;
                }
                StringBuilder key = new StringBuilder(permissions.size());
                for (String permission : permissions) {
                    key.append(player.hasPermission(permission) ? '1' : '0');
                }
                player.sendMessage(rendered.computeIfAbsent(key.toString(), ignored -> alert.render(player::hasPermission)));
            }
        } catch (RuntimeException failure) {
            NexusLog.warn(LogTopic.GENERAL, Tr.t("Diffusion d'une alerte impossible"), failure);
        }
    }
}
