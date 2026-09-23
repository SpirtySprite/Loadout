package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Tr;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record KitSchedule(long from, long until, Set<DayOfWeek> days, LocalTime opens, LocalTime closes) {

    public static final KitSchedule ALWAYS = new KitSchedule(0L, 0L, Set.of(), null, null);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter LOOSE_TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final String[] DAY_NAMES = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};

    public KitSchedule {
        days = days == null || days.isEmpty() ? Set.of() : Set.copyOf(days);
        if (opens == null || closes == null) {
            opens = null;
            closes = null;
        }
    }

    public boolean always() {
        return from <= 0L && until <= 0L && days.isEmpty() && opens == null;
    }

    public boolean open(long now, ZoneId zone) {
        if (from > 0L && now < from) {
            return false;
        }
        if (until > 0L && now >= until) {
            return false;
        }
        ZonedDateTime current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone);
        if (!days.isEmpty() && !days.contains(current.getDayOfWeek())) {
            return false;
        }
        return opens == null || within(current.toLocalTime(), opens, closes);
    }

    public boolean expired(long now) {
        return until > 0L && now >= until;
    }

    public long nextOpening(long now, ZoneId zone) {
        if (open(now, zone)) {
            return now;
        }
        if (expired(now)) {
            return 0L;
        }
        long start = Math.max(now, from);
        ZonedDateTime cursor = ZonedDateTime.ofInstant(Instant.ofEpochMilli(start), zone).withSecond(0).withNano(0);
        for (int step = 0; step < 8 * 24 * 60 / 15 + 1; step++) {
            long at = cursor.toInstant().toEpochMilli();
            if (at >= start && open(at, zone)) {
                return at;
            }
            cursor = cursor.plusMinutes(15 - cursor.getMinute() % 15L);
        }
        return 0L;
    }

    static boolean within(LocalTime time, LocalTime opens, LocalTime closes) {
        if (opens.equals(closes)) {
            return true;
        }
        if (opens.isBefore(closes)) {
            return !time.isBefore(opens) && time.isBefore(closes);
        }
        return !time.isBefore(opens) || time.isBefore(closes);
    }

    public List<String> describe(ZoneId zone) {
        List<String> lines = new ArrayList<>();
        if (from > 0L) {
            lines.add(Tr.t("À partir du ") + DATE.format(Instant.ofEpochMilli(from).atZone(zone)));
        }
        if (until > 0L) {
            lines.add(Tr.t("Jusqu'au ") + DATE.format(Instant.ofEpochMilli(until).atZone(zone)));
        }
        if (!days.isEmpty()) {
            lines.add(Tr.t("Le ") + dayList(days));
        }
        if (opens != null) {
            lines.add(Tr.t("De ") + TIME.format(opens) + Tr.t(" à ") + TIME.format(closes));
        }
        return lines;
    }

    static String dayList(Set<DayOfWeek> days) {
        List<String> names = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (days.contains(day)) {
                names.add(dayName(day));
            }
        }
        return String.join(", ", names);
    }

    public static String dayName(DayOfWeek day) {
        return DAY_NAMES[day.getValue() - 1];
    }

    public static DayOfWeek parseDay(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        for (int index = 0; index < DAY_NAMES.length; index++) {
            if (DAY_NAMES[index].equals(text)) {
                return DayOfWeek.of(index + 1);
            }
        }
        try {
            return DayOfWeek.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static Set<DayOfWeek> parseDays(List<String> raw) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String entry : raw) {
            DayOfWeek day = parseDay(entry);
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    public static long parseDate(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String text = raw.trim();
        try {
            return LocalDateTime.parse(text, DATE).atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException invalid) {
            try {
                return LocalDateTime.parse(text + " 00:00", DATE).atZone(zone).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                return -1L;
            }
        }
    }

    public static String writeDate(long at, ZoneId zone) {
        return at <= 0L ? "" : DATE.format(Instant.ofEpochMilli(at).atZone(zone));
    }

    public static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT).replace('h', ':');
        if (text.endsWith(":")) {
            text = text + "00";
        }
        if (!text.contains(":")) {
            text = text + ":00";
        }
        try {
            return LocalTime.parse(text, LOOSE_TIME);
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    public static LocalTime[] parseHours(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("-");
        if (parts.length != 2) {
            return null;
        }
        LocalTime opens = parseTime(parts[0]);
        LocalTime closes = parseTime(parts[1]);
        return opens == null || closes == null ? null : new LocalTime[]{opens, closes};
    }

    public String writeHours() {
        return opens == null ? "" : TIME.format(opens) + "-" + TIME.format(closes);
    }
}
