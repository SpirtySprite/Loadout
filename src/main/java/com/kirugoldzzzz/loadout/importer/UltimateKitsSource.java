package com.kirugoldzzzz.loadout.importer;

import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UltimateKitsSource implements KitSource {

    @Override
    public String id() {
        return "ultimatekits";
    }

    @Override
    public String plugin() {
        return "UltimateKits";
    }

    @Override
    public Imported.Result read(File pluginFolder) {
        Imported.Result result = new Imported.Result(plugin());
        ConfigurationSection kits = YamlConfiguration.loadConfiguration(new File(pluginFolder, "kit.yml"))
                .getConfigurationSection("Kits");
        if (kits == null) {
            result.warn(Tr.t("Aucun kit trouvé dans kit.yml"));
            return result;
        }
        String currency = YamlConfiguration.loadConfiguration(new File(pluginFolder, "config.yml"))
                .getString("Main.Currency Symbol", "$");
        Map<String, String> ids = new LinkedHashMap<>();
        for (String key : kits.getKeys(false)) {
            ConfigurationSection section = kits.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Imported.Kit kit = kit(key, section, currency, result);
            ids.put(key, kit.id());
            result.add(kit);
        }
        ConfigurationSection data = YamlConfiguration.loadConfiguration(new File(pluginFolder, "data.yml"))
                .getConfigurationSection("Kits");
        if (data != null) {
            ids.forEach((key, id) -> {
                ConfigurationSection delays = data.getConfigurationSection(key + ".delays");
                if (delays == null) {
                    return;
                }
                for (String player : delays.getKeys(false)) {
                    try {
                        result.claim(UUID.fromString(player), id, delays.getLong(player));
                    } catch (IllegalArgumentException invalid) {
                        result.warn(key + Tr.t(" : joueur illisible ") + player);
                    }
                }
            });
        }
        return result;
    }

    static Imported.Kit kit(String key, ConfigurationSection section, String currency, Imported.Result result) {
        List<Imported.Item> items = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        double money = 0.0D;
        for (String raw : section.getStringList("items")) {
            String line = raw.contains(";") && !raw.startsWith("{") ? raw.substring(raw.indexOf(';') + 1) : raw;
            line = line.trim();
            if (line.startsWith(currency)) {
                try {
                    money += Double.parseDouble(line.substring(currency.length()).trim());
                } catch (NumberFormatException invalid) {
                    result.warn(key + Tr.t(" : montant illisible ") + line);
                }
            } else if (line.startsWith("/")) {
                commands.add(KitSource.command(line));
            } else if (line.startsWith("{")) {
                try {
                    Snbt.Item item = Snbt.item(line);
                    items.add(Imported.Item.argument(item.argument(), item.count()));
                } catch (IllegalArgumentException invalid) {
                    result.warn(key + Tr.t(" : objet illisible, ") + invalid.getMessage());
                }
            } else if (!line.isEmpty()) {
                result.warn(key + Tr.t(" : objet inconnu ") + line);
            }
        }
        long delay = section.getLong("delay", 0L);
        String title = section.getString("title");
        return new Imported.Kit(KitSource.slug(key), title == null || title.isBlank() ? key : ImportText.mini(title),
                Math.max(0L, delay), delay < 0L, Math.max(0.0D, section.getDouble("price", 0.0D)), items, commands,
                money);
    }
}
