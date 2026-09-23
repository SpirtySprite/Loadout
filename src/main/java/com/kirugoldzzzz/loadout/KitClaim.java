package com.kirugoldzzzz.loadout;

import java.util.UUID;

public record KitClaim(UUID player, String kit, int uses, long first, long last, int tier, int streak, int best) {

    public KitClaim {
        uses = Math.max(0, uses);
        tier = Math.max(0, tier);
        streak = Math.max(0, streak);
        best = Math.max(streak, best);
    }

    public KitClaim(UUID player, String kit, int uses, long first, long last) {
        this(player, kit, uses, first, last, 0, 0, 0);
    }

    public static KitClaim first(UUID player, String kit, long now) {
        return new KitClaim(player, kit, 1, now, now, 0, 0, 0);
    }

    public KitClaim again(long now) {
        return new KitClaim(player, kit, uses == Integer.MAX_VALUE ? uses : uses + 1, first <= 0L ? now : first, now,
                tier, streak, best);
    }

    public KitClaim withStreak(int value) {
        return new KitClaim(player, kit, uses, first, last, tier, value, Math.max(best, value));
    }

    public KitClaim withTier(int value) {
        return new KitClaim(player, kit, uses, first, last, value, streak, best);
    }

    public KitClaim withUses(int value, long lastClaim) {
        return new KitClaim(player, kit, value, first, lastClaim, tier, streak, best);
    }
}
