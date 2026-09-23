package com.kirugoldzzzz.loadout.importer;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface KitSource {

    List<KitSource> ALL = List.of(new EssentialsKitsSource(), new UltimateKitsSource());

    String id();

    String plugin();

    Imported.Result read(File pluginFolder);

    static Optional<KitSource> byId(String id) {
        String wanted = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return ALL.stream().filter(source -> source.id().equals(wanted)).findFirst();
    }

    static String slug(String raw) {
        String slug = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim().replace(' ', '_')
                .replaceAll("[^a-z0-9_-]", "");
        return slug.isEmpty() ? "imported" : slug;
    }

    static String command(String raw) {
        String text = raw.startsWith("/") ? raw.substring(1) : raw;
        return text.replace("{USERNAME}", "<player>").replace("{PLAYER}", "<player>").replace("{player}", "<player>")
                .replace("%player%", "<player>");
    }
}
