package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.anim.Ease;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;

import java.util.ArrayList;
import java.util.List;

public final class KitRitualCeremony implements KitCeremony {

    static final int DRAW = 24;
    static final int BEAMS = 34;
    static final int SEAL = 44;
    static final int SUMMON = 48;
    static final int STEP = 4;
    static final int PROCESSION = 18;
    static final int FLIGHT = 16;
    static final int END = 112;
    static final int CANDLES = 4;
    static final double GROUND = -0.45D;
    static final double CIRCLE = 1.6D;
    static final double PROCESSION_RADIUS = 1.25D;

    private final List<BlockDisplay> candles = new ArrayList<>();

    @Override
    public int duration() {
        return END;
    }

    @Override
    public void tick(KitStage stage, int tick) {
        if (tick == 0) {
            stage.sound(Sound.BLOCK_SCULK_SPREAD, 0.7F, 0.6F);
            for (int index = 0; index < CANDLES; index++) {
                candles.add(stage.block(stage.show().pedestalMaterial(), stage.show().primary()));
            }
        }
        if (tick <= DRAW) {
            draw(stage, tick);
        } else if (tick < END - 16) {
            stage.ring(GROUND, CIRCLE, 32, stage.show().primary(), 0.7F, tick * 0.02D);
            if (tick % 3 == 0) {
                stage.ring(GROUND, CIRCLE * 0.55D, 16, stage.show().accent(), 0.6F, -tick * 0.05D);
            }
        }
        candles(stage, tick);
        if (tick >= BEAMS && tick < SEAL) {
            beams(stage, tick);
        }
        if (tick == SEAL) {
            seal(stage);
        }
        core(stage, tick);
        for (int index = 0; index < stage.count(); index++) {
            summoned(stage, tick, index);
        }
        if (tick == END - 16) {
            stage.sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.5F, 1.4F);
        }
    }

    private void draw(KitStage stage, int tick) {
        double sweep = Math.PI * 2.0D * Ease.progress(tick, 0, DRAW);
        stage.arc(GROUND, CIRCLE, 40, sweep, stage.show().primary(), 0.9F);
        if (tick % 4 == 0) {
            stage.sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.6F + (float) sweep * 0.12F);
        }
    }

    private void candles(KitStage stage, int tick) {
        for (int index = 0; index < candles.size(); index++) {
            double angle = Math.toRadians(index * 360.0D / CANDLES);
            double rise = Ease.outBack(Ease.progress(tick, 6 + index * 3, 14));
            double sink = Ease.inCubic(Ease.progress(tick, END - 16, 16));
            double scale = 0.3D * rise * (1.0D - sink);
            double y = Ease.lerp(-1.1D, GROUND, rise) + (tick >= SEAL ? 0.15D : 0.0D);
            stage.poseBlock(candles.get(index), Math.cos(angle) * CIRCLE, y, Math.sin(angle) * CIRCLE,
                    tick * 1.5D, scale, 3);
            if (rise >= 1.0D && sink <= 0.0D && tick % 6 == index % 6) {
                stage.dust(Math.cos(angle) * CIRCLE, y + 0.35D, Math.sin(angle) * CIRCLE, stage.show().accent(), 0.8F);
            }
        }
    }

    private void beams(KitStage stage, int tick) {
        double reach = Ease.progress(tick, BEAMS, SEAL - BEAMS);
        for (int index = 0; index < CANDLES; index++) {
            double angle = Math.toRadians(index * 360.0D / CANDLES);
            for (int step = 0; step <= 8; step++) {
                double travel = step / 8.0D * reach;
                double radius = CIRCLE * (1.0D - travel);
                stage.dust(Math.cos(angle) * radius, GROUND + 0.2D + travel * 0.9D, Math.sin(angle) * radius,
                        stage.show().accent(), 0.6F);
            }
        }
        if (tick == BEAMS) {
            stage.sound(Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.0F);
        }
    }

    private void seal(KitStage stage) {
        stage.claimTitle(Card.noteLine(Palette.SECONDARY, "✦", stage.show().name()), 30);
        stage.flash(0.0D, 0.8D, 0.0D, stage.show().accent());
        stage.particle(Particle.END_ROD, 0.0D, 0.8D, 0.0D, 30, 0.25D, 0.12D);
        for (int ring = 0; ring < 3; ring++) {
            stage.ring(GROUND, 0.7D + ring * 0.55D, 24, ring % 2 == 0 ? stage.show().accent()
                    : stage.show().primary(), 1.2F, 0.0D);
        }
        stage.sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.9F, 1.2F);
        stage.sound(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6F, 0.8F);
        stage.firework(FireworkEffect.Type.STAR, true, false, 1.2D);
    }

    private void core(KitStage stage, int tick) {
        if (tick < BEAMS) {
            return;
        }
        double materialise = Ease.progress(tick, BEAMS, SEAL - BEAMS);
        double pulse = materialise < 1.0D ? Math.abs(Math.sin(materialise * Math.PI * 3.0D)) : 1.0D;
        double settle = Ease.outBack(Ease.progress(tick, SEAL, 10));
        double vanish = Ease.inCubic(Ease.progress(tick, END - 12, 12));
        double scale = (materialise < 1.0D ? 0.9D * pulse : Ease.lerp(0.9D, 1.25D, settle)) * (1.0D - vanish);
        double y = 0.85D + Math.sin(tick * 0.12D) * 0.06D;
        stage.pose(stage.core(), 0.0D, y, 0.0D, tick * 3.0D, 0.0D, scale, scale, 3);
    }

    private void summoned(KitStage stage, int tick, int index) {
        int born = SUMMON + index * STEP;
        if (tick < born) {
            return;
        }
        int count = Math.max(1, stage.count());
        double emerge = Ease.outCubic(Ease.progress(tick, born, 10));
        double angle = Math.toRadians(index * 360.0D / count) + (tick - born) * 0.03D;
        double y = Ease.lerp(-0.9D, 0.55D, emerge);
        double scale = 0.42D * emerge;
        double x = Math.cos(angle) * PROCESSION_RADIUS;
        double z = Math.sin(angle) * PROCESSION_RADIUS;
        if (tick == born) {
            stage.particle(Particle.SCULK_SOUL, x, GROUND, z, 6, 0.1D, 0.02D);
            stage.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 0.9F + index * 0.05F);
        }
        int start = SUMMON + PROCESSION + index * STEP;
        if (tick >= start) {
            double flight = Ease.inOutCubic(Ease.progress(tick, start, FLIGHT));
            double[] target = stage.toPlayer();
            x = Ease.lerp(x, target[0], flight);
            z = Ease.lerp(z, target[2], flight);
            y = Ease.lerp(y, target[1], flight) + 0.5D * 4.0D * flight * (1.0D - flight);
            scale *= 1.0D - 0.85D * flight;
            if (flight < 1.0D && tick % 2 == 0) {
                stage.dust(x, y, z, stage.show().accent(), 0.7F);
            }
            if (flight >= 1.0D) {
                stage.hide(stage.item(index));
                if (tick == start + FLIGHT) {
                    stage.sound(Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.0F + index * 0.04F);
                }
                return;
            }
        }
        stage.pose(stage.item(index), x, y, z, tick * 6.0D + index * 40.0D, scale);
    }
}
