package com.kirugoldzzzz.loadout.importer;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ImportText {

    private static final Pattern LEGACY = Pattern.compile("[&§][0-9a-fk-orA-FK-OR#]");
    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private ImportText() {
    }

    public static String mini(String raw) {
        if (raw == null) {
            return null;
        }
        String text = PLACEHOLDER.matcher(raw).replaceAll("").strip();
        if (!LEGACY.matcher(text).find()) {
            return text;
        }
        return MiniMessage.miniMessage().serialize(AMPERSAND.deserialize(text.replace('§', '&')));
    }

    public static List<String> mini(List<String> raw) {
        List<String> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            lines.add(mini(line));
        }
        return lines;
    }
}
