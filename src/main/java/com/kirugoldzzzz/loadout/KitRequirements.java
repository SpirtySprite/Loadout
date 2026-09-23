package com.kirugoldzzzz.loadout;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record KitRequirements(long playtime, List<String> kits, Set<String> worlds, double balance, int level,
                              KitSchedule schedule, List<KitStatistic> statistics) {

    public static final KitRequirements NONE = new KitRequirements(0L, List.of(), Set.of(), 0.0D, 0,
            KitSchedule.ALWAYS, List.of());

    public KitRequirements(long playtime, List<String> kits, Set<String> worlds, double balance, int level,
                           KitSchedule schedule) {
        this(playtime, kits, worlds, balance, level, schedule, List.of());
    }

    public KitRequirements {
        playtime = Math.max(0L, playtime);
        kits = kits == null ? List.of() : kits.stream()
                .map(kit -> kit.trim().toLowerCase(Locale.ROOT))
                .filter(kit -> !kit.isEmpty())
                .distinct()
                .toList();
        worlds = worlds == null ? Set.of() : worlds.stream()
                .map(world -> world.trim().toLowerCase(Locale.ROOT))
                .filter(world -> !world.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        balance = Double.isFinite(balance) ? Math.max(0.0D, balance) : 0.0D;
        level = Math.max(0, level);
        schedule = schedule == null ? KitSchedule.ALWAYS : schedule;
        statistics = statistics == null ? List.of() : List.copyOf(statistics);
    }

    public boolean none() {
        return playtime <= 0L && kits.isEmpty() && worlds.isEmpty() && balance <= 0.0D && level <= 0
                && schedule.always() && statistics.isEmpty();
    }
}
