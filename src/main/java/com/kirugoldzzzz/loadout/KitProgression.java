package com.kirugoldzzzz.loadout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record KitProgression(KitMastery mastery, Streaks streaks, Map<Integer, KitRewards> collection,
                             Featured featured) {

    public static final KitProgression NONE = new KitProgression(KitMastery.NONE, Streaks.NONE, Map.of(),
            Featured.NONE);

    public record Streaks(boolean enabled, int bonusPerClaim, int maximumBonus, long warning,
                          Map<Integer, KitRewards> milestones) {

        public static final Streaks NONE = new Streaks(false, 0, 0, 0L, Map.of());

        public Streaks {
            bonusPerClaim = Math.max(0, Math.min(100, bonusPerClaim));
            maximumBonus = Math.max(0, Math.min(1_000, maximumBonus));
            warning = Math.max(0L, warning);
            milestones = milestones == null ? Map.of() : new TreeMap<>(milestones);
        }

        public int bonus(int streak) {
            if (!enabled || streak <= 1) {
                return 0;
            }
            long raw = (long) bonusPerClaim * (streak - 1L);
            return (int) Math.min(maximumBonus, raw);
        }

        public Integer nextMilestone(int streak) {
            for (Integer at : milestones.keySet()) {
                if (at > streak) {
                    return at;
                }
            }
            return null;
        }
    }

    public record Featured(boolean enabled, int discount, List<String> kits, LocalTime rotation) {

        public static final Featured NONE = new Featured(false, 0, List.of(), LocalTime.MIDNIGHT);

        public Featured {
            discount = Math.max(0, Math.min(90, discount));
            kits = kits == null ? List.of() : List.copyOf(kits);
            rotation = rotation == null ? LocalTime.MIDNIGHT : rotation;
        }

        public String pick(List<String> candidates, long now, ZoneId zone) {
            if (!enabled || candidates.isEmpty()) {
                return null;
            }
            long start = KitReset.DAILY.periodStart(now, zone, rotation, null);
            LocalDate day = ZonedDateTime.ofInstant(Instant.ofEpochMilli(start), zone).toLocalDate();
            List<String> ordered = new ArrayList<>(candidates);
            int index = (int) Math.floorMod(day.toEpochDay(), (long) ordered.size());
            return ordered.get(index);
        }

        public long nextRotation(long now, ZoneId zone) {
            return KitReset.DAILY.nextReset(now, zone, rotation, null);
        }
    }

    public KitProgression {
        mastery = mastery == null ? KitMastery.NONE : mastery;
        streaks = streaks == null ? Streaks.NONE : streaks;
        collection = collection == null ? Map.of() : new TreeMap<>(collection);
        featured = featured == null ? Featured.NONE : featured;
    }

    public static int nextStreak(Kit kit, KitClaim previous, long now, ZoneId zone) {
        if (kit.reset() == KitReset.NONE) {
            return 0;
        }
        if (previous == null || previous.last() <= 0L) {
            return 1;
        }
        long current = kit.reset().periodStart(now, zone, kit.resetTime(), kit.resetDay());
        long last = kit.reset().periodStart(previous.last(), zone, kit.resetTime(), kit.resetDay());
        if (last >= current) {
            return Math.max(1, previous.streak());
        }
        long before = kit.reset().periodStart(current - 1L, zone, kit.resetTime(), kit.resetDay());
        return last == before ? Math.max(1, previous.streak()) + 1 : 1;
    }

    public static int liveStreak(Kit kit, KitClaim claim, long now, ZoneId zone) {
        if (kit.reset() == KitReset.NONE || claim == null || claim.last() <= 0L || claim.streak() <= 0) {
            return 0;
        }
        long current = kit.reset().periodStart(now, zone, kit.resetTime(), kit.resetDay());
        long last = kit.reset().periodStart(claim.last(), zone, kit.resetTime(), kit.resetDay());
        if (last >= current) {
            return claim.streak();
        }
        long before = kit.reset().periodStart(current - 1L, zone, kit.resetTime(), kit.resetDay());
        return last == before ? claim.streak() : 0;
    }

    public static boolean streakAtRisk(Kit kit, KitClaim claim, long now, ZoneId zone, long warning) {
        if (warning <= 0L || kit.reset() == KitReset.NONE || claim == null || claim.streak() < 2) {
            return false;
        }
        long current = kit.reset().periodStart(now, zone, kit.resetTime(), kit.resetDay());
        long last = kit.reset().periodStart(claim.last(), zone, kit.resetTime(), kit.resetDay());
        if (last >= current) {
            return false;
        }
        long before = kit.reset().periodStart(current - 1L, zone, kit.resetTime(), kit.resetDay());
        if (last != before) {
            return false;
        }
        long next = kit.reset().nextReset(now, zone, kit.resetTime(), kit.resetDay());
        return next - now <= warning;
    }
}
