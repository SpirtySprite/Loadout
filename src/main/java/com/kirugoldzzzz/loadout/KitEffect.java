package com.kirugoldzzzz.loadout;

import java.util.Locale;

public record KitEffect(String type, int seconds, int amplifier) {

    static final int MAXIMUM_SECONDS = 86_400;
    static final int MAXIMUM_AMPLIFIER = 9;

    public KitEffect {
        type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        seconds = Math.max(1, Math.min(MAXIMUM_SECONDS, seconds));
        amplifier = Math.max(0, Math.min(MAXIMUM_AMPLIFIER, amplifier));
    }

    public static KitEffect parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("\\s*:\\s*|\\s+");
        String type = parts[0];
        if (type.equalsIgnoreCase("minecraft") && parts.length > 1) {
            String[] shifted = new String[parts.length - 1];
            System.arraycopy(parts, 1, shifted, 0, shifted.length);
            parts = shifted;
            type = parts[0];
        }
        if (type.isBlank()) {
            return null;
        }
        long seconds = parts.length > 1 ? KitDurations.parseOr(parts[1], -1L) / 1_000L : 30L;
        if (seconds <= 0L) {
            return null;
        }
        int level = 1;
        if (parts.length > 2) {
            try {
                level = Integer.parseInt(parts[2]);
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        return new KitEffect(type, (int) Math.min(MAXIMUM_SECONDS, seconds), level - 1);
    }

    public String write() {
        return type + ":" + seconds + "s:" + (amplifier + 1);
    }

    public String label() {
        String name = type.replace('_', ' ');
        if (name.isEmpty()) {
            return "?";
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + roman(amplifier + 1);
    }

    static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }
}
