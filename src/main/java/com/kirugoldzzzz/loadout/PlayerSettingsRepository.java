package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class PlayerSettingsRepository {

    private static final Logger LOGGER = Logger.getLogger("Loadout");
    private static final String MUTED_REMINDERS = "reminders-off";

    private final File file;
    private final Set<UUID> remindersOff = ConcurrentHashMap.newKeySet();

    public PlayerSettingsRepository(File file) {
        this.file = file;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList(MUTED_REMINDERS)) {
            try {
                remindersOff.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
        }
    }

    public PlayerSettings get(UUID player) {
        return new PlayerSettings(!remindersOff.contains(player));
    }

    public void toggle(UUID player, PlayerSettings.Setting setting) {
        if (setting == PlayerSettings.Setting.KIT_REMINDERS && !remindersOff.remove(player)) {
            remindersOff.add(player);
        }
        List<String> snapshot = remindersOff.stream().map(UUID::toString).sorted().toList();
        Scheduling.async(() -> save(snapshot));
    }

    private synchronized void save(List<String> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(MUTED_REMINDERS, snapshot);
        try {
            yaml.save(file);
        } catch (IOException failure) {
            LOGGER.warning("Impossible d'enregistrer les préférences : " + failure.getMessage());
        }
    }
}
