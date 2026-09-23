package com.kirugoldzzzz.loadout;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KitDurations {

    static final long MAXIMUM = TimeUnit.DAYS.toMillis(3_650);
    private static final Pattern PART = Pattern.compile("(\\d{1,7})\\s*([a-z]*)");

    private KitDurations() {
    }

    public static OptionalLong parse(String input) {
        if (input == null) {
            return OptionalLong.empty();
        }
        String text = input.trim().toLowerCase(Locale.ROOT).replace(",", " ");
        if (text.isEmpty()) {
            return OptionalLong.empty();
        }
        if (text.equals("0") || text.equals("aucun") || text.equals("aucune") || text.equals("none")) {
            return OptionalLong.of(0L);
        }
        Matcher matcher = PART.matcher(text);
        long total = 0L;
        int consumed = 0;
        boolean found = false;
        while (matcher.find()) {
            if (!text.substring(consumed, matcher.start()).isBlank()) {
                return OptionalLong.empty();
            }
            long unit = unit(matcher.group(2));
            if (unit <= 0L) {
                return OptionalLong.empty();
            }
            long value = Long.parseLong(matcher.group(1));
            if (value > MAXIMUM / unit) {
                return OptionalLong.empty();
            }
            total += value * unit;
            if (total > MAXIMUM) {
                return OptionalLong.empty();
            }
            consumed = matcher.end();
            found = true;
        }
        if (!found || !text.substring(consumed).isBlank()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(total);
    }

    public static long parseOr(String input, long fallback) {
        return parse(input).orElse(fallback);
    }

    public static String write(long millis) {
        if (millis <= 0L) {
            return "0";
        }
        long seconds = millis / 1_000L;
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long rest = seconds % 60L;
        StringBuilder text = new StringBuilder();
        append(text, days, "j");
        append(text, hours, "h");
        append(text, minutes, "m");
        append(text, rest, "s");
        return text.isEmpty() ? "0" : text.toString();
    }

    private static void append(StringBuilder text, long value, String unit) {
        if (value > 0L) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(value).append(unit);
        }
    }

    private static long unit(String suffix) {
        return switch (suffix) {
            case "", "s", "sec", "secs", "seconde", "secondes", "second", "seconds" -> 1_000L;
            case "m", "min", "mins", "minute", "minutes" -> 60_000L;
            case "h", "hr", "hrs", "heure", "heures", "hour", "hours" -> 3_600_000L;
            case "j", "d", "jour", "jours", "day", "days" -> 86_400_000L;
            case "sem", "semaine", "semaines", "w", "week", "weeks" -> 604_800_000L;
            case "mois", "mo", "month", "months" -> 2_592_000_000L;
            default -> -1L;
        };
    }
}
