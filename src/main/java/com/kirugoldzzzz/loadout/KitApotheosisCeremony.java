package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.anim.Ease;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;

import java.util.ArrayList;
import java.util.List;

public final class KitApotheosisCeremony implements KitCeremony {

    static final int ECLIPSE = 26;
    static final int ASCEND = 58;
    static final int REVEAL = 92;
    static final int RAIN = 108;
    static final int DROP = 3;
    static final int BOUNCE = 12;
    static final int FLIGHT = 10;
    static final int FINALE = 170;
    static final int END = 196;
    static final int PILLARS = 10;
    static final int SATELLITES = 4;
    static final double GROUND = -0.55D;
    static final double PILLAR_RADIUS = 1.75D;
    static final double SKY = 3.6D;
    private static final Color GLOOM = Color.fromRGB(0x1F1B2E);

    private final List<BlockDisplay> pillars = new ArrayList<>();
    private final List<ItemDisplay> satellites = new ArrayList<>();
    private ItemDisplay prize;

    @Override
    public int duration() {
        return END;
    }

    @Override
    public void tick(KitStage stage, int tick) {
        if (tick == 0) {
            eclipse(stage);
        }
        if (tick < ECLIPSE) {
            gloom(stage, tick);
        }
        pillars(stage, tick);
        if (tick == ECLIPSE) {
            stage.sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 0.7F);
            stage.sound(Sound.ENTITY_WITHER_SPAWN, 0.35F, 1.6F);
            for (int index = 0; index < SATELLITES; index++) {
                satellites.add(stage.ghost(stage.kit().icon() == null ? stage.best() : KitStyle.icon(stage.kit()),
                        true, stage.show().accent()));
            }
        }
        core(stage, tick);
        satellites(stage, tick);
        if (tick >= ECLIPSE && tick < REVEAL && tick % 2 == 0) {
            helix(stage, tick);
        }
        if (tick == REVEAL) {
            reveal(stage);
        }
        prize(stage, tick);
        for (int index = 0; index < stage.count(); index++) {
            rain(stage, tick, index);
        }
        if (tick == FINALE) {
            finale(stage);
        }
        if (tick > FINALE && tick < END && tick % 6 == 0) {
            stage.firework(FireworkEffect.Type.BURST, true, true, 2.4D);
        }
    }

    private void eclipse(KitStage stage) {
        stage.sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7F, 0.6F);
        stage.sound(Sound.BLOCK_PORTAL_AMBIENT, 0.5F, 0.5F);
        for (int index = 0; index < PILLARS; index++) {
            pillars.add(stage.block(stage.show().pedestalMaterial(), stage.show().primary()));
        }
    }

    private void gloom(KitStage stage, int tick) {
        double close = Ease.outCubic(Ease.progress(tick, 0, ECLIPSE));
        stage.ring(GROUND + 0.1D, 3.2D * (1.0D - close) + 0.6D, 28, GLOOM, 1.4F, tick * 0.06D);
        if (tick % 3 == 0) {
            stage.particle(Particle.SCULK_SOUL, 0.0D, 0.4D, 0.0D, 2, 0.8D, 0.01D);
            stage.particle(Particle.SOUL, 0.0D, GROUND, 0.0D, 3, 1.2D, 0.02D);
        }
        if (tick % 6 == 0) {
            stage.sound(Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.25F, 0.6F);
        }
    }

    private void pillars(KitStage stage, int tick) {
        for (int index = 0; index < pillars.size(); index++) {
            double angle = Math.toRadians(index * 360.0D / PILLARS) + tick * 0.012D;
            double rise = Ease.outBack(Ease.progress(tick, 4 + index, 18));
            double lift = Ease.outCubic(Ease.progress(tick, ECLIPSE, 20)) * 0.4D;
            double launch = Ease.inCubic(Ease.progress(tick, FINALE, 16));
            double scale = 0.32D * rise * (1.0D - launch);
            double y = Ease.lerp(-1.4D, GROUND, rise) + lift + launch * 4.5D;
            stage.poseBlock(pillars.get(index), Math.cos(angle) * PILLAR_RADIUS, y, Math.sin(angle) * PILLAR_RADIUS,
                    tick * 2.0D, scale, 3);
            if (tick >= ECLIPSE && tick < FINALE && tick % 10 == index % 10) {
                stage.dust(Math.cos(angle) * PILLAR_RADIUS, y + 0.4D, Math.sin(angle) * PILLAR_RADIUS,
                        stage.show().accent(), 0.9F);
            }
        }
    }

    private void core(KitStage stage, int tick) {
        if (tick < 6) {
            return;
        }
        double ascend = Ease.inOutCubic(Ease.progress(tick, ECLIPSE, ASCEND - ECLIPSE));
        double grow = Ease.outBack(Ease.progress(tick, 6, 16));
        double shift = Ease.inOutCubic(Ease.progress(tick, REVEAL, 14));
        double collapse = Ease.inCubic(Ease.progress(tick, FINALE, 14));
        double y = Ease.lerp(GROUND + 0.2D, 1.7D, ascend);
        double scale = grow * Ease.lerp(0.6D, 1.7D, ascend) * Ease.lerp(1.0D, 0.75D, shift) * (1.0D - collapse);
        double spin = tick * (tick < ASCEND ? 9.0D : 4.0D) + collapse * 1_080.0D;
        double lean = Ease.lerp(0.0D, 12.0D, shift);
        stage.pose(stage.core(), 0.0D, y + Math.sin(tick * 0.1D) * 0.06D, shift * 0.6D, spin, lean, scale, scale, 3);
    }

    private void satellites(KitStage stage, int tick) {
        for (int index = 0; index < satellites.size(); index++) {
            double appear = Ease.outBack(Ease.progress(tick, ECLIPSE + index * 3, 14));
            double collapse = Ease.inCubic(Ease.progress(tick, FINALE, 12));
            double angle = Math.toRadians(index * 360.0D / Math.max(1, satellites.size())) - tick * 0.045D;
            double radius = 2.5D * appear * (1.0D - collapse);
            double y = 1.2D + Math.sin(tick * 0.09D + index * 1.6D) * 0.55D;
            double scale = 0.34D * appear * (1.0D - collapse);
            stage.pose(satellites.get(index), Math.cos(angle) * radius, y, Math.sin(angle) * radius, tick * 7.0D,
                    18.0D, scale, scale, 3);
        }
    }

    private void helix(KitStage stage, int tick) {
        for (int strand = 0; strand < 2; strand++) {
            double angle = tick * 0.35D + strand * Math.PI;
            double height = GROUND + ((tick * 0.08D + strand * 1.3D) % 2.6D);
            stage.dust(Math.cos(angle) * 0.75D, height, Math.sin(angle) * 0.75D,
                    strand == 0 ? stage.show().primary() : stage.show().accent(), 1.0F);
        }
    }

    private void reveal(KitStage stage) {
        stage.claimTitle(Card.noteLine(Palette.ERROR, "✦", stage.show().name()), 44);
        prize = stage.ghost(stage.best(), true, stage.show().accent());
        stage.flash(0.0D, 1.5D, 0.6D, stage.show().accent());
        stage.particle(Particle.END_ROD, 0.0D, 1.5D, 0.6D, 40, 0.3D, 0.1D);
        stage.sound(Sound.ITEM_TOTEM_USE, 0.6F, 1.2F);
        stage.sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.8F, 1.0F);
        for (int index = 0; index < 3; index++) {
            stage.firework(FireworkEffect.Type.STAR, true, true, 2.0D);
        }
    }

    private void prize(KitStage stage, int tick) {
        if (prize == null) {
            return;
        }
        double grow = Ease.outBack(Ease.progress(tick, REVEAL, 14));
        double flight = Ease.inOutCubic(Ease.progress(tick, FINALE - 10, 14));
        double[] target = stage.toPlayer();
        double scale = 2.0D * grow * (1.0D - 0.9D * flight);
        double x = Ease.lerp(0.0D, target[0], flight);
        double y = Ease.lerp(1.45D + Math.sin(tick * 0.12D) * 0.07D, target[1], flight);
        double z = Ease.lerp(-0.85D, target[2], flight);
        stage.pose(prize, x, y, z, tick * 5.0D, 0.0D, scale, scale, 3);
        if (tick % 3 == 0 && flight <= 0.0D) {
            double angle = tick * 0.25D;
            stage.dust(Math.cos(angle) * 0.85D, 1.45D, -0.85D + Math.sin(angle) * 0.85D, stage.show().accent(), 1.1F);
        }
        if (flight >= 1.0D) {
            stage.hide(prize);
        }
    }

    private void rain(KitStage stage, int tick, int index) {
        int born = RAIN + index * DROP;
        if (tick < born) {
            return;
        }
        int count = Math.max(1, stage.count());
        double fall = Ease.inCubic(Ease.progress(tick, born, BOUNCE));
        double angle = Math.toRadians(index * 360.0D / count + 18.0D * index);
        double radius = 1.05D;
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        double y = Ease.lerp(SKY, 0.25D, fall);
        double scale = 0.45D;
        if (tick == born) {
            stage.dust(x, SKY, z, stage.show().accent(), 1.0F);
        }
        if (tick == born + BOUNCE) {
            stage.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 0.9F + index * 0.05F);
            stage.particle(Particle.END_ROD, x, 0.25D, z, 6, 0.1D, 0.03D);
        }
        int start = born + BOUNCE + 4;
        if (tick >= start) {
            double flight = Ease.inOutCubic(Ease.progress(tick, start, FLIGHT));
            double[] target = stage.toPlayer();
            x = Ease.lerp(x, target[0], flight);
            z = Ease.lerp(z, target[2], flight);
            y = Ease.lerp(0.25D, target[1], flight) + 0.55D * 4.0D * flight * (1.0D - flight);
            scale *= 1.0D - 0.85D * flight;
            if (flight < 1.0D && tick % 2 == 0) {
                stage.dust(x, y, z, stage.show().primary(), 0.8F);
            }
            if (flight >= 1.0D) {
                stage.hide(stage.item(index));
                if (tick == start + FLIGHT) {
                    stage.sound(Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.1F + index * 0.04F);
                }
                return;
            }
        } else if (fall < 1.0D && tick % 2 == 0) {
            stage.dust(x, y, z, stage.show().accent(), 0.7F);
        }
        stage.pose(stage.item(index), x, y, z, tick * 11.0D + index * 25.0D, scale);
    }

    private void finale(KitStage stage) {
        stage.flash(0.0D, 1.4D, 0.0D, stage.show().primary());
        stage.particle(Particle.EXPLOSION_EMITTER, 0.0D, 1.2D, 0.0D, 1, 0.0D, 0.0D);
        stage.particleAtPlayer(Particle.TOTEM_OF_UNDYING, 80, 0.6D, 0.4D);
        for (int ring = 0; ring < 5; ring++) {
            stage.ring(GROUND, 0.5D + ring * 0.7D, 30, ring % 2 == 0 ? stage.show().accent()
                    : stage.show().primary(), 1.4F, ring * 0.2D);
        }
        stage.sound(Sound.ENTITY_ENDER_DRAGON_DEATH, 0.35F, 1.4F);
        stage.sound(Sound.ITEM_TOTEM_USE, 0.8F, 0.9F);
        stage.sound(Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
        for (int index = 0; index < 5; index++) {
            stage.firework(index % 2 == 0 ? FireworkEffect.Type.STAR : FireworkEffect.Type.BALL_LARGE, true, true,
                    2.6D);
        }
    }
}
