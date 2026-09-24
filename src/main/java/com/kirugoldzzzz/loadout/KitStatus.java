package com.kirugoldzzzz.loadout;

import java.util.List;

public record KitStatus(State state, long readyAt, long remaining, int usesLeft, int stockLeft, int reduction,
                        List<Check> checks) {

    public enum State {
        AVAILABLE(true),
        COOLDOWN(false),
        EXHAUSTED(false),
        SOLD_OUT(false),
        LOCKED(false),
        CLOSED(false),
        REQUIREMENTS(false),
        UNAFFORDABLE(false);

        private final boolean claimable;

        State(boolean claimable) {
            this.claimable = claimable;
        }

        public boolean claimable() {
            return claimable;
        }
    }

    public enum Kind {
        PERMISSION,
        SCHEDULE,
        PLAYTIME,
        KITS,
        WORLD,
        BALANCE,
        LEVEL,
        STATISTIC,
        CONDITION,
        TEAM,
        MONEY,
        SHARDS,
        LEVELS
    }

    public record Check(Kind kind, boolean met, String label, String detail) {
    }

    public KitStatus {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public boolean available() {
        return state.claimable();
    }

    public boolean unmet(Kind kind) {
        for (Check check : checks) {
            if (check.kind() == kind && !check.met()) {
                return true;
            }
        }
        return false;
    }
}
