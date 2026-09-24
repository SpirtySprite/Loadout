package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Tr;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class KitPoints {

    private static volatile String name = "Points";
    private static volatile String balance = "";
    private static volatile String take = "";
    private static volatile String give = "";
    private static volatile String team = "";

    private KitPoints() {
    }

    public static void configure(ConfigurationSection points, ConfigurationSection teams) {
        name = text(points, "name", Tr.t("Points"));
        balance = text(points, "balance", "");
        take = text(points, "take", "");
        give = text(points, "give", "");
        team = text(teams, "placeholder", "");
    }

    public static String label() {
        return name;
    }

    public static boolean enabled() {
        return !balance.isBlank() && !take.isBlank() && placeholders();
    }

    public static boolean teamsEnabled() {
        return !team.isBlank() && placeholders();
    }

    public static long balance(Player player) {
        if (!enabled()) {
            return 0L;
        }
        return parse(PlaceholderAPI.setPlaceholders(player, balance));
    }

    public static void take(Player player, long amount) {
        run(take, player, amount);
    }

    public static void give(Player player, long amount) {
        if (!give.isBlank() && placeholders()) {
            run(give, player, amount);
        }
    }

    public static String placeholder(Player player, String raw) {
        return placeholders() ? PlaceholderAPI.setPlaceholders(player, raw) : raw;
    }

    public static UUID team(Player player) {
        if (!teamsEnabled()) {
            return null;
        }
        String value = PlaceholderAPI.setPlaceholders(player, team).strip();
        if (value.isEmpty() || value.equals(team) || value.equalsIgnoreCase("none") || value.equals("-")) {
            return null;
        }
        return UUID.nameUUIDFromBytes(("loadout-team:" + value.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    static long parse(String raw) {
        if (raw == null) {
            return 0L;
        }
        String digits = raw.replaceAll("[\\s,_]", "");
        int dot = digits.indexOf('.');
        if (dot >= 0) {
            digits = digits.substring(0, dot);
        }
        digits = digits.replaceAll("[^0-9-]", "");
        try {
            return Math.max(0L, Long.parseLong(digits));
        } catch (NumberFormatException invalid) {
            return 0L;
        }
    }

    private static void run(String template, Player player, long amount) {
        if (amount <= 0L || template.isBlank()) {
            return;
        }
        String command = template.replace("{player}", player.getName()).replace("<player>", player.getName())
                .replace("{amount}", String.valueOf(amount)).replace("<amount>", String.valueOf(amount));
        String line = command.startsWith("/") ? command.substring(1) : command;
        Scheduling.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
    }

    private static boolean placeholders() {
        return Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private static String text(ConfigurationSection section, String key, String fallback) {
        String value = section == null ? null : section.getString(key);
        return value == null ? fallback : value.strip();
    }
}
