package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.anim.Ease;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.Sound;

public final class KitStormCeremony implements KitCeremony {

    static final int WIND = 10;
    static final int VORTEX = 54;
    static final int COLLAPSE = 62;
    static final int VOLLEY = 68;
    static final int SHOT = 3;
    static final int FLIGHT = 7;
    static final int END = 126;
    static final double TOP = 2.3D;
    static final double BASE = -0.4D;

    @Override
    public int duration() {
        return END;
    }

    @Override
    public void tick(KitStage stage, int tick) {
        if (tick == 0) {
            stage.sound(Sound.ENTITY_PHANTOM_FLAP, 0.6F, 0.6F);
            stage.sound(Sound.ITEM_TRIDENT_THUNDER, 0.25F, 0.5F);
        }
        if (tick < WIND) {
            stage.ring(BASE, 1.9D - tick * 0.08D, 20, stage.show().primary(), 0.8F, tick * 0.3D);
        }
        if (tick >= WIND && tick < COLLAPSE) {
            funnel(stage, tick);
        }
        if (tick == COLLAPSE) {
            collapse(stage);
        }
        core(stage, tick);
        for (int index = 0; index < stage.count(); index++) {
            debris(stage, tick, index);
        }
        if (tick > COLLAPSE && tick < END - 20 && tick % 6 == 0) {
            stage.ring(BASE, 0.9D, 14, stage.show().accent(), 0.7F, tick * 0.25D);
        }
    }

    private void funnel(KitStage stage, int tick) {
        double speed = Ease.progress(tick, WIND, VORTEX - WIND);
        for (int layer = 0; layer < 6; layer++) {
            double height = BASE + layer * 0.45D;
            double radius = 0.35D + layer * 0.22D + Math.sin(tick * 0.2D + layer) * 0.08D;
            double angle = tick * (0.25D + speed * 0.35D) + layer * 0.9D;
            stage.dust(Math.cos(angle) * radius, height, Math.sin(angle) * radius, layer % 2 == 0
                    ? stage.show().primary() : stage.show().accent(), 0.9F);
        }
        if (tick % 8 == 0) {
            stage.sound(Sound.ENTITY_PHANTOM_FLAP, 0.35F, 0.6F + (float) speed * 0.8F);
        }
        if (tick % 16 == 0) {
            stage.sound(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.4F, 0.8F + (float) speed);
        }
    }

    private void collapse(KitStage stage) {
        stage.claimTitle(Card.noteLine(Palette.WARNING, "✦", stage.show().name()), 34);
        stage.flash(0.0D, 1.0D, 0.0D, stage.show().accent());
        stage.particle(Particle.EXPLOSION, 0.0D, 0.9D, 0.0D, 1, 0.0D, 0.0D);
        stage.particle(Particle.FIREWORK, 0.0D, 0.9D, 0.0D, 40, 0.2D, 0.3D);
        for (int ring = 0; ring < 4; ring++) {
            stage.ring(BASE, 0.5D + ring * 0.6D, 28, ring % 2 == 0 ? stage.show().accent() : stage.show().primary(),
                    1.3F, 0.0D);
        }
        stage.sound(Sound.ITEM_TRIDENT_THUNDER, 1.0F, 1.0F);
        stage.sound(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5F, 1.4F);
        for (int index = 0; index < 3; index++) {
            stage.firework(FireworkEffect.Type.STAR, true, true, 1.6D);
        }
    }

    private void core(KitStage stage, int tick) {
        double grow = Ease.outBack(Ease.progress(tick, 2, 12));
        double drop = Ease.inOutCubic(Ease.progress(tick, COLLAPSE, 10));
        double vanish = Ease.inCubic(Ease.progress(tick, END - 18, 18));
        double y = Ease.lerp(TOP, 1.1D, drop);
        double scale = 1.1D * grow * (1.0D - vanish) * (1.0D + 0.25D * drop);
        double spin = tick < COLLAPSE ? tick * 22.0D : COLLAPSE * 22.0D + (tick - COLLAPSE) * 8.0D;
        stage.pose(stage.core(), 0.0D, y + Math.sin(tick * 0.2D) * 0.05D, 0.0D, spin, 0.0D, scale, scale, 2);
    }

    private void debris(KitStage stage, int tick, int index) {
        int count = Math.max(1, stage.count());
        double born = 4 + index;
        if (tick < born) {
            return;
        }
        double appear = Ease.outBack(Ease.progress(tick, (int) born, 8));
        double speed = 0.16D + Ease.progress(tick, WIND, VORTEX - WIND) * 0.3D;
        double helix = index % 2 == 0 ? 1.0D : -1.0D;
        double angle = Math.toRadians(index * 360.0D / count) + tick * speed * helix;
        double lift = ((tick * 0.05D + index * 0.4D) % 2.6D);
        double y = BASE + lift;
        double radius = (0.45D + lift * 0.28D) * appear;
        double suck = Ease.inCubic(Ease.progress(tick, COLLAPSE - 6, 8));
        radius *= 1.0D - suck * 0.85D;
        y = Ease.lerp(y, 1.1D, suck);
        double scale = 0.4D * appear * (1.0D + 0.3D * suck);
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        int start = VOLLEY + index * SHOT;
        if (tick >= start) {
            double flight = Ease.inCubic(Ease.progress(tick, start, FLIGHT));
            double[] target = stage.toPlayer();
            x = Ease.lerp(x, target[0], flight);
            z = Ease.lerp(z, target[2], flight);
            y = Ease.lerp(y, target[1], flight);
            scale *= 1.0D - 0.9D * flight;
            if (tick == start) {
                stage.sound(Sound.ENTITY_ARROW_SHOOT, 0.5F, 1.4F + index * 0.05F);
            }
            if (flight < 1.0D) {
                stage.dust(x, y, z, stage.show().accent(), 0.8F);
                stage.particle(Particle.CRIT, x, y, z, 2, 0.05D, 0.0D);
            } else {
                stage.hide(stage.item(index));
                if (tick == start + FLIGHT) {
                    stage.sound(Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.5F);
                    stage.particleAtPlayer(Particle.CRIT, 6, 0.2D, 0.05D);
                }
                return;
            }
        }
        stage.pose(stage.item(index), x, y, z, tick * 16.0D, scale);
    }
}
