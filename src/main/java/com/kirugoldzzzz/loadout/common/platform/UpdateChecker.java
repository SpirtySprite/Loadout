package com.kirugoldzzzz.loadout.common.platform;

import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Tr;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker implements Listener {

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final Plugin plugin;
    private final String repository;
    private final String permission;
    private volatile String latest;

    public UpdateChecker(Plugin plugin, String repository, String permission) {
        this.plugin = plugin;
        this.repository = repository;
        this.permission = permission;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repository + "/releases/latest"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", plugin.getName())
                .GET()
                .build();
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        accept(response.body());
                    }
                })
                .exceptionally(failure -> null);
    }

    public String latest() {
        return latest;
    }

    public String page() {
        return "https://github.com/" + repository + "/releases/latest";
    }

    void accept(String body) {
        Matcher matcher = TAG.matcher(body);
        if (!matcher.find()) {
            return;
        }
        String found = matcher.group(1);
        String installed = plugin.getPluginMeta().getVersion();
        if (newer(found, installed)) {
            latest = found;
            plugin.getLogger().info(Tr.t("Nouvelle version disponible : ") + found
                    + Tr.t(", version installée : ") + installed + " " + page());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        String found = latest;
        Player player = event.getPlayer();
        if (found == null || !player.hasPermission(permission)) {
            return;
        }
        Component link = Component.text(page()).clickEvent(ClickEvent.openUrl(page()));
        Messages.send(player, "general.update-available",
                Mini.value("plugin", plugin.getName()),
                Mini.value("version", found),
                Mini.value("current", plugin.getPluginMeta().getVersion()),
                Mini.component("link", link));
    }

    public static boolean newer(String candidate, String installed) {
        int[] left = parts(candidate);
        int[] right = parts(installed);
        for (int index = 0; index < Math.max(left.length, right.length); index++) {
            int a = index < left.length ? left[index] : 0;
            int b = index < right.length ? right[index] : 0;
            if (a != b) {
                return a > b;
            }
        }
        return false;
    }

    private static int[] parts(String version) {
        String core = version.split("[-+]", 2)[0];
        Matcher matcher = NUMBER.matcher(core);
        int[] values = new int[4];
        int count = 0;
        while (matcher.find() && count < values.length) {
            try {
                values[count++] = Integer.parseInt(matcher.group());
            } catch (NumberFormatException overflow) {
                values[count - 1] = Integer.MAX_VALUE;
            }
        }
        return java.util.Arrays.copyOf(values, count);
    }
}
