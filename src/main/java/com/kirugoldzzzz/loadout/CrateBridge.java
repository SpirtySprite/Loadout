package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Tr;

import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CrateBridge {

    public record KeyCrate(String id, String displayName) {
    }

    private static final String DEFAULT_COMMAND = "cle give {player} {crate} {amount}";

    private final File lootriftCrates;
    private volatile String command = DEFAULT_COMMAND;
    private volatile List<KeyCrate> configured = List.of();

    public CrateBridge(File pluginsFolder) {
        this.lootriftCrates = new File(pluginsFolder, "Lootrift/crates.yml");
    }

    public void configure(ConfigurationSection section) {
        command = section == null ? DEFAULT_COMMAND : section.getString("command", DEFAULT_COMMAND);
        List<KeyCrate> list = new ArrayList<>();
        ConfigurationSection crates = section == null ? null : section.getConfigurationSection("crates");
        if (crates != null) {
            for (String id : crates.getKeys(false)) {
                list.add(new KeyCrate(id, crates.getString(id, id)));
            }
        }
        configured = List.copyOf(list);
    }

    public List<KeyCrate> crates() {
        if (!configured.isEmpty() || !lootriftCrates.isFile()) {
            return configured;
        }
        ConfigurationSection crates = YamlConfiguration.loadConfiguration(lootriftCrates).getConfigurationSection("crates");
        if (crates == null) {
            return List.of();
        }
        List<KeyCrate> list = new ArrayList<>();
        for (String id : crates.getKeys(false)) {
            list.add(new KeyCrate(id, crates.getString(id + ".name", id)));
        }
        return list;
    }

    public Optional<KeyCrate> crate(String id) {
        for (KeyCrate crate : crates()) {
            if (crate.id().equalsIgnoreCase(id)) {
                return Optional.of(crate);
            }
        }
        return Optional.empty();
    }

    public void give(UUID player, String crate, int amount) {
        if (amount <= 0 || command == null || command.isBlank()) {
            return;
        }
        String name = Bukkit.getOfflinePlayer(player).getName();
        if (name == null) {
            return;
        }
        String resolved = command.replace("{player}", name).replace("{crate}", crate)
                .replace("{amount}", String.valueOf(amount));
        String line = resolved.startsWith("/") ? resolved.substring(1) : resolved;
        Scheduling.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
    }
}
