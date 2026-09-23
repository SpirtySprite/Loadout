package com.kirugoldzzzz.loadout;

public record KitCost(double money, long shards, int levels) {

    public static final KitCost FREE = new KitCost(0.0D, 0L, 0);

    public KitCost {
        money = Double.isFinite(money) ? Math.max(0.0D, money) : 0.0D;
        shards = Math.max(0L, shards);
        levels = Math.max(0, levels);
    }

    public boolean free() {
        return money <= 0.0D && shards <= 0L && levels <= 0;
    }
}
