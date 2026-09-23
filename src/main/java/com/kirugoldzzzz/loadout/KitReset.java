package com.kirugoldzzzz.loadout;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public enum KitReset {

    NONE("aucune", "Aucune"),
    DAILY("quotidienne", "Chaque jour"),
    WEEKLY("hebdomadaire", "Chaque semaine"),
    MONTHLY("mensuelle", "Chaque mois");

    private final String id;
    private final String label;

    KitReset(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public KitReset next() {
        KitReset[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static KitReset parse(String raw) {
        if (raw == null) {
            return NONE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "quotidienne", "quotidien", "daily", "jour" -> DAILY;
            case "hebdomadaire", "weekly", "semaine" -> WEEKLY;
            case "mensuelle", "mensuel", "monthly", "mois" -> MONTHLY;
            default -> NONE;
        };
    }

    public long periodStart(long now, ZoneId zone, LocalTime time, DayOfWeek day) {
        if (this == NONE) {
            return 0L;
        }
        ZonedDateTime current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone);
        LocalTime at = time == null ? LocalTime.MIDNIGHT : time;
        ZonedDateTime start = switch (this) {
            case DAILY -> current.toLocalDate().atTime(at).atZone(zone);
            case WEEKLY -> current.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(day == null ? DayOfWeek.MONDAY : day))
                    .atTime(at).atZone(zone);
            case MONTHLY -> current.toLocalDate().withDayOfMonth(1).atTime(at).atZone(zone);
            case NONE -> current;
        };
        if (start.isAfter(current)) {
            start = switch (this) {
                case DAILY -> start.minusDays(1);
                case WEEKLY -> start.minusWeeks(1);
                case MONTHLY -> start.minusMonths(1);
                case NONE -> start;
            };
        }
        return start.toInstant().toEpochMilli();
    }

    public long nextReset(long after, ZoneId zone, LocalTime time, DayOfWeek day) {
        if (this == NONE) {
            return 0L;
        }
        ZonedDateTime start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(periodStart(after, zone, time, day)),
                zone);
        ZonedDateTime next = switch (this) {
            case DAILY -> start.plusDays(1);
            case WEEKLY -> start.plusWeeks(1);
            case MONTHLY -> {
                LocalDate firstOfNext = start.toLocalDate().withDayOfMonth(1).plusMonths(1);
                yield firstOfNext.atTime(time == null ? LocalTime.MIDNIGHT : time).atZone(zone);
            }
            case NONE -> start;
        };
        return next.toInstant().toEpochMilli();
    }
}
