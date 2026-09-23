package com.kirugoldzzzz.loadout.common.config;

import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.NexusLog;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class ConfigFile {

    private final Plugin plugin;
    private final String name;
    private final File file;
    private final List<String> managedSections;

    private YamlConfiguration configuration;

    public ConfigFile(Plugin plugin, String name, String... managedSections) {
        this.plugin = plugin;
        this.name = name;
        this.file = new File(plugin.getDataFolder(), name);
        this.managedSections = List.of(managedSections);
    }

    public ConfigFile load() {
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        configuration = YamlConfiguration.loadConfiguration(file);
        applyDefaults();
        return this;
    }

    private void applyDefaults() {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String section : managedSections) {
                bundled.set(section, null);
            }
            configuration.setDefaults(bundled);
            configuration.options().copyDefaults(true);
        } catch (Exception exception) {
            NexusLog.warn(LogTopic.CONFIG, Tr.t("Impossible de lire les valeurs par défaut de ") + name + " : " + exception.getMessage());
        }
    }

    public boolean seed(String path) {
        if (get().contains(path, true)) {
            return false;
        }
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return false;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationSection source = bundled.getConfigurationSection(path);
            if (source == null) {
                return false;
            }
            Sections.copy(source, configuration.createSection(path));
            return save();
        } catch (Exception exception) {
            NexusLog.warn(LogTopic.CONFIG, Tr.t("Impossible de compléter ") + name + " : " + exception.getMessage());
            return false;
        }
    }

    public YamlConfiguration get() {
        if (configuration == null) {
            load();
        }
        return configuration;
    }

    public boolean save() {
        if (configuration == null) {
            return false;
        }
        try {
            configuration.save(file);
            return true;
        } catch (Exception exception) {
            NexusLog.error(LogTopic.CONFIG, Tr.t("Impossible de sauvegarder ") + name + " : " + exception.getMessage());
            return false;
        }
    }

    public boolean replaceWithDefaultsWhenOlder(String versionPath, int version) {
        if (get().contains(versionPath, true) && get().getInt(versionPath, 0) >= version) {
            return false;
        }
        try {
            if (file.exists()) {
                File backup = new File(plugin.getDataFolder(), name + ".before-v" + version + ".bak");
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.saveResource(name, true);
            load();
            return true;
        } catch (Exception exception) {
            NexusLog.error(LogTopic.CONFIG, Tr.t("Impossible de mettre à niveau ") + name + " : " + exception.getMessage());
            return false;
        }
    }

    public String name() {
        return name;
    }

    public File file() {
        return file;
    }
}
