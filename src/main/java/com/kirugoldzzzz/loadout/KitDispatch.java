package com.kirugoldzzzz.loadout;

import java.util.Locale;

public record KitDispatch(boolean console, String command) {

    private static final String CONSOLE = "console:";
    private static final String PLAYER = "joueur:";
    private static final String PLAYER_ALIAS = "player:";

    public KitDispatch {
        String trimmed = command == null ? "" : command.trim();
        command = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    public static KitDispatch parse(String raw) {
        if (raw == null) {
            return new KitDispatch(true, "");
        }
        String text = raw.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith(CONSOLE)) {
            return new KitDispatch(true, text.substring(CONSOLE.length()));
        }
        if (lower.startsWith(PLAYER)) {
            return new KitDispatch(false, text.substring(PLAYER.length()));
        }
        if (lower.startsWith(PLAYER_ALIAS)) {
            return new KitDispatch(false, text.substring(PLAYER_ALIAS.length()));
        }
        return new KitDispatch(true, text);
    }

    public boolean valid() {
        return !command.isBlank();
    }

    public String write() {
        return (console ? CONSOLE : PLAYER) + " " + command;
    }

    public String resolve(String player, String uuid, String kit) {
        return command.replace("<player>", player).replace("<joueur>", player)
                .replace("<uuid>", uuid).replace("<kit>", kit);
    }
}
