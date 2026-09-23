package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.anim.Ease;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Particle;
import org.bukkit.Sound;

public final class KitBloomCeremony implements KitCeremony {

    static final int SEED = 14;
    static final int RISE = 28;
    static final int BLOOM = 40;
    static final int SPIRAL = 56;
    static final int FLIGHT = 14;
    static final int END = 78;
    static final double GROUND = -0.4D;
    static final double SEED_RADIUS = 1.35D;
    static final double PETAL_RADIUS = 1.75D;

    @Override
    public int duration() {
        return END;
    }

    @Override
    public void tick(KitStage stage, int tick) {
        if (tick == 0) {
            stage.sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7F, 0.8F);
        }
        if (tick < RISE && tick % 2 == 0) {
            stage.ring(GROUND, SEED_RADIUS * Ease.outCubic(Ease.progress(tick, 0, SEED)), 16,
                    stage.show().primary(), 0.8F, tick * 0.04D);
        }
        if (tick == RISE) {
            stage.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.2F);
        }
        if (tick == BLOOM) {
            bloom(stage);
        }
        if (tick > BLOOM && tick < SPIRAL && tick % 3 == 0) {
            stage.ring(0.9D, 0.5D, 10, stage.show().accent(), 0.7F, tick * 0.2D);
        }
        core(stage, tick);
        for (int index = 0; index < stage.count(); index++) {
            petal(stage, tick, index);
        }
    }

    private void bloom(KitStage stage) {
        stage.claimTitle(Card.noteLine(Palette.PRIMARY, "✦", stage.show().name()), 30);
        stage.flash(0.0D, 0.9D, 0.0D, stage.show().accent());
        stage.particle(Particle.END_ROD, 0.0D, 0.9D, 0.0D, 28, 0.2D, 0.1D);
        stage.sound(Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9F, 1.4F);
        stage.sound(Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.8F);
        stage.firework(org.bukkit.FireworkEffect.Type.BALL, true, false, 1.0D);
    }

    private void core(KitStage stage, int tick) {
        if (tick < BLOOM - 6) {
            return;
        }
        double grow = Ease.outBack(Ease.progress(tick, BLOOM - 6, 10));
        double pulse = 1.0D + Math.sin(tick * 0.3D) * 0.06D;
        double vanish = Ease.inCubic(Ease.progress(tick, END - 12, 12));
        double scale = 1.15D * grow * pulse * (1.0D - vanish);
        stage.pose(stage.core(), 0.0D, 0.9D + Math.sin(tick * 0.18D) * 0.05D, 0.0D, tick * 5.0D + vanish * 540.0D,
                0.0D, scale, scale, 2);
    }

    private void petal(KitStage stage, int tick, int index) {
        int count = Math.max(1, stage.count());
        double base = Math.toRadians(index * 360.0D / count);
        double seed = Ease.outBack(Ease.progress(tick, index, 8));
        double rise = Ease.inOutCubic(Ease.progress(tick, SEED + index, RISE - SEED));
        double converge = Ease.inCubic(Ease.progress(tick, RISE, BLOOM - RISE));
        double scatter = Ease.outBack(Ease.progress(tick, BLOOM, 12));
        double radius = SEED_RADIUS * seed;
        radius = Ease.lerp(radius, 0.18D, converge);
        radius = Ease.lerp(radius, PETAL_RADIUS, scatter);
        double angle = base + rise * Math.PI * 0.6D + tick * 0.035D;
        double y = Ease.lerp(GROUND, 0.9D, rise);
        double pitch = Ease.lerp(90.0D, 0.0D, rise);
        double scale = 0.38D * seed * (1.0D + 0.35D * scatter);
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        if (tick < RISE && tick % 4 == 0) {
            stage.dust(x, GROUND + 0.05D, z, stage.show().primary(), 0.7F);
        }
        int start = SPIRAL + index * 2;
        if (tick >= start) {
            double flight = Ease.inOutCubic(Ease.progress(tick, start, FLIGHT));
            double[] target = stage.toPlayer();
            double spiralAngle = angle + flight * Math.PI * 2.0D;
            double spiralRadius = PETAL_RADIUS * (1.0D - flight);
            x = Ease.lerp(Math.cos(spiralAngle) * spiralRadius, target[0], flight);
            z = Ease.lerp(Math.sin(spiralAngle) * spiralRadius, target[2], flight);
            y = Ease.lerp(y, target[1], flight);
            scale *= 1.0D - 0.85D * flight;
            if (tick % 2 == 0 && flight < 1.0D) {
                stage.dust(x, y, z, stage.show().accent(), 0.6F);
            }
            if (flight >= 1.0D) {
                stage.hide(stage.item(index));
                if (tick == start + FLIGHT) {
                    stage.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.0F + index * 0.08F);
                }
                return;
            }
        }
        stage.pose(stage.item(index), x, y, z, tick * 9.0D + index * 30.0D, pitch, scale, scale, 2);
    }
}
