package com.kirugoldzzzz.loadout;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

public record KitViewer(UUID id, Predicate<String> permissions, long playtime, double balance, long shards, int level,
                        String world, UUID team, ToLongFunction<KitStatistic> statistics) {

    public static final String BYPASS_COOLDOWN = "loadout.kit.bypass.cooldown";
    public static final String BYPASS_COST = "loadout.kit.bypass.cost";
    public static final String BYPASS_REQUIREMENTS = "loadout.kit.bypass.requirements";
    public static final String BYPASS_PERMISSION = "loadout.kit.bypass.permission";

    public KitViewer {
        permissions = permissions == null ? ignored -> false : permissions;
        statistics = statistics == null ? ignored -> 0L : statistics;
    }

    public KitViewer(UUID id, Predicate<String> permissions, long playtime, double balance, long shards, int level,
                     String world) {
        this(id, permissions, playtime, balance, shards, level, world, null, null);
    }

    public boolean has(String permission) {
        return permission == null || permissions.test(permission);
    }

    public UUID owner(Kit kit) {
        return kit.options().team() && team != null ? team : id;
    }
}
