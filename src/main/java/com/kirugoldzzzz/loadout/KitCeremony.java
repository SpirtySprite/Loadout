package com.kirugoldzzzz.loadout;

public interface KitCeremony {

    int duration();

    void tick(KitStage stage, int tick);

    static KitCeremony of(KitShow show) {
        return switch (show.level()) {
            case 0 -> new KitDropCeremony();
            case 1 -> new KitBloomCeremony();
            case 2 -> new KitRitualCeremony();
            case 3 -> new KitStormCeremony();
            default -> new KitApotheosisCeremony();
        };
    }
}
