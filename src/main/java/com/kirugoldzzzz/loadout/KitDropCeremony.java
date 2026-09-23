package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.anim.Ease;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Particle;
import org.bukkit.Sound;

public final class KitDropCeremony implements KitCeremony {

    static final int LAND = 10;
    static final int BOUNCE = 6;
    static final int OPEN = 18;
    static final int GATHER = 26;
    static final int FLIGHT = 9;
    static final int END = 44;
    static final double FALL = 3.2D;
    static final double REST = 0.45D;
    static final double FAN = 0.85D;

    @Override
    public int duration() {
        return END;
    }

    @Override
    public void tick(KitStage stage, int tick) {
        if (tick == 0) {
            stage.sound(Sound.ENTITY_ITEM_PICKUP, 0.4F, 0.7F);
        }
        core(stage, tick);
        if (tick == LAND) {
            stage.sound(Sound.BLOCK_CHEST_OPEN, 0.8F, 1.1F);
            stage.sound(Sound.BLOCK_STONE_HIT, 0.6F, 0.8F);
            stage.ring(-0.35D, 0.55D, 14, stage.show().primary(), 1.0F, 0.0D);
            stage.particle(Particle.CLOUD, 0.0D, -0.3D, 0.0D, 8, 0.25D, 0.03D);
        }
        if (tick == OPEN) {
            stage.claimTitle(Card.noteLine(Palette.SUCCESS, Palette.CHECK, "Kit récupéré"), 24);
            stage.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.5F);
            stage.particle(Particle.FIREWORK, 0.0D, REST, 0.0D, 18, 0.12D, 0.16D);
        }
        for (int index = 0; index < stage.count(); index++) {
            item(stage, tick, index);
        }
    }

    private void core(KitStage stage, int tick) {
        double fall = Ease.inCubic(Ease.progress(tick, 0, LAND));
        double y = Ease.lerp(FALL, REST, fall);
        double squash = tick >= LAND && tick < LAND + BOUNCE
                ? Ease.outBack(Ease.progress(tick, LAND, BOUNCE)) : 1.0D;
        double vanish = Ease.inCubic(Ease.progress(tick, END - 8, 8));
        double scale = 0.85D * (1.0D - vanish);
        double scaleY = squash < 1.0D ? scale * Ease.lerp(0.5D, 1.0D, squash) : scale;
        double hop = tick >= LAND ? Math.max(0.0D, 0.18D * Math.sin(Ease.progress(tick, LAND, BOUNCE) * Math.PI)) : 0.0D;
        stage.pose(stage.core(), 0.0D, y + hop, 0.0D, tick * 6.0D + fall * 180.0D, 0.0D, scale, scaleY, 2);
    }

    private void item(KitStage stage, int tick, int index) {
        int born = OPEN + index;
        if (tick < born) {
            return;
        }
        int count = Math.max(1, stage.count());
        double spread = Math.toRadians(150.0D);
        double angle = -spread / 2.0D + spread * index / Math.max(1, count - 1);
        double out = Ease.outCubic(Ease.progress(tick, born, 8));
        double x = Math.sin(angle) * FAN * out;
        double z = Math.cos(angle) * FAN * out;
        double y = REST + Math.sin(out * Math.PI) * 0.5D;
        double scale = 0.4D * Ease.outBack(Ease.progress(tick, born, 6));
        int start = GATHER + index;
        if (tick >= start) {
            double flight = Ease.inOutCubic(Ease.progress(tick, start, FLIGHT));
            double[] target = stage.toPlayer();
            x = Ease.lerp(x, target[0], flight);
            z = Ease.lerp(z, target[2], flight);
            y = Ease.lerp(y, target[1], flight) + 0.4D * 4.0D * flight * (1.0D - flight);
            scale *= 1.0D - 0.9D * flight;
            if (flight >= 1.0D) {
                stage.hide(stage.item(index));
                if (tick == start + FLIGHT) {
                    stage.sound(Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.2F + index * 0.05F);
                }
                return;
            }
        }
        stage.pose(stage.item(index), x, y, z, tick * 12.0D, scale);
    }
}
