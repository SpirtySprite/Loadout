package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.effect.Particles;
import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.NexusLog;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Mini;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class KitStage {

    static final double TARGET_HEIGHT = 1.1D;
    private static final Set<Particle> WARNED = ConcurrentHashMap.newKeySet();

    private final Player player;
    private final Kit kit;
    private final KitShow show;
    private final Location anchor;
    private final List<ItemStack> items;
    private final ItemStack best;
    private final ItemStack icon;
    private final Map<Integer, ItemDisplay> pool = new HashMap<>();
    private final List<ItemDisplay> displays = new ArrayList<>();
    private final List<BlockDisplay> blocks = new ArrayList<>();
    private final Map<Entity, Transformation> applied = new IdentityHashMap<>();

    private ItemDisplay core;
    private double[] target = {0.0D, KitStage.TARGET_HEIGHT, 0.0D};

    KitStage(Player player, Kit kit, KitShow show, Location anchor, List<ItemStack> items, ItemStack best,
             ItemStack icon) {
        this.player = player;
        this.kit = kit;
        this.show = show;
        this.anchor = anchor;
        this.items = items;
        this.best = best;
        this.icon = icon;
    }

    public Player player() {
        return player;
    }

    public Kit kit() {
        return kit;
    }

    public KitShow show() {
        return show;
    }

    public Location anchor() {
        return anchor;
    }

    public int count() {
        return items.size();
    }

    public ItemStack best() {
        return best == null ? icon : best;
    }

    public ItemDisplay core() {
        if (core == null) {
            core = ghost(icon, true, show.primary());
        }
        return core;
    }

    public ItemDisplay item(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return pool.computeIfAbsent(index, slot -> ghost(items.get(slot), false, null));
    }

    public ItemDisplay ghost(ItemStack item, boolean fixed, Color glow) {
        World world = anchor.getWorld();
        if (world == null) {
            return null;
        }
        ItemDisplay display = world.spawn(anchor, ItemDisplay.class, spawned -> {
            spawned.setItemStack(item);
            spawned.setItemDisplayTransform(fixed ? ItemDisplay.ItemDisplayTransform.FIXED
                    : ItemDisplay.ItemDisplayTransform.GROUND);
            spawned.setPersistent(false);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setShadowRadius(0.0F);
            spawned.setVisibleByDefault(false);
            spawned.setTransformation(zero());
            if (glow != null) {
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(glow);
            }
        });
        displays.add(display);
        reveal(display);
        return display;
    }

    public BlockDisplay block(Material material, Color glow) {
        World world = anchor.getWorld();
        if (world == null) {
            return null;
        }
        BlockDisplay display = world.spawn(anchor, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            spawned.setPersistent(false);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setShadowRadius(0.0F);
            spawned.setVisibleByDefault(false);
            spawned.setTransformation(zero());
            if (glow != null) {
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(glow);
            }
        });
        blocks.add(display);
        reveal(display);
        return display;
    }

    public void pose(ItemDisplay display, double x, double y, double z, double yaw, double scale) {
        pose(display, x, y, z, yaw, 0.0D, scale, scale, 2);
    }

    public void pose(ItemDisplay display, double x, double y, double z, double yaw, double pitch, double scale,
                     double scaleY, int duration) {
        if (display == null || !display.isValid()) {
            return;
        }
        Transformation next = new Transformation(vector(x, y, z), rotation(yaw, pitch),
                vector(Math.max(0.0D, scale), Math.max(0.0D, scaleY), Math.max(0.0D, scale)), new Quaternionf());
        write(display, next, duration);
    }

    public void poseBlock(BlockDisplay display, double x, double y, double z, double yaw, double scale, int duration) {
        if (display == null || !display.isValid()) {
            return;
        }
        double half = Math.max(0.0D, scale) / 2.0D;
        Transformation next = new Transformation(vector(x - half, y, z - half), rotation(yaw, 0.0D),
                vector(Math.max(0.0D, scale), Math.max(0.0D, scale), Math.max(0.0D, scale)), new Quaternionf());
        write(display, next, duration);
    }

    private void write(Display display, Transformation next, int duration) {
        if (next.equals(applied.get(display))) {
            return;
        }
        applied.put(display, next);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformation(next);
    }

    public void hide(ItemDisplay display) {
        pose(display, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1);
    }

    public double[] toPlayer() {
        try {
            Location at = player.getLocation();
            if (at.getWorld() != null && at.getWorld().equals(anchor.getWorld())) {
                target = new double[]{at.getX() - anchor.getX(), at.getY() + TARGET_HEIGHT - anchor.getY(),
                        at.getZ() - anchor.getZ()};
            }
        } catch (RuntimeException unreachable) {
            return target;
        }
        return target;
    }

    public void dust(double x, double y, double z, Color color, float size) {
        try {
            Particles.show(player, Particle.DUST, anchor.clone().add(x, y, z), 1, 0.0D, 0.0D, 0.0D, 0.0D,
                    new Particle.DustOptions(color, size));
        } catch (RuntimeException invalid) {
            unsupported(Particle.DUST);
        }
    }

    public void ring(double y, double radius, int points, Color color, float size, double phase) {
        for (int index = 0; index < points; index++) {
            double angle = Math.toRadians(index * 360.0D / points) + phase;
            dust(Math.cos(angle) * radius, y, Math.sin(angle) * radius, color, size);
        }
    }

    public void arc(double y, double radius, int points, double sweep, Color color, float size) {
        for (int index = 0; index < points; index++) {
            double angle = sweep * index / Math.max(1, points);
            dust(Math.cos(angle) * radius, y, Math.sin(angle) * radius, color, size);
        }
    }

    public void particle(Particle particle, double x, double y, double z, int count, double spread, double speed) {
        emit(particle, anchor.clone().add(x, y, z), count, spread, speed, show.accent());
    }

    public void particleAtPlayer(Particle particle, int count, double spread, double speed) {
        try {
            emit(particle, player.getLocation().add(0.0D, TARGET_HEIGHT, 0.0D), count, spread, speed, show.accent());
        } catch (RuntimeException unreachable) {
            emit(particle, anchor.clone().add(0.0D, TARGET_HEIGHT, 0.0D), count, spread, speed, show.accent());
        }
    }

    public void flash(double x, double y, double z, Color color) {
        emit(Particle.FLASH, anchor.clone().add(x, y, z), 1, 0.0D, 0.0D, color);
    }

    private void emit(Particle particle, Location at, int count, double spread, double speed, Color color) {
        Class<?> data = particle.getDataType();
        try {
            if (data == Void.class) {
                Particles.show(player, particle, at, count, spread, spread, spread, speed);
            } else if (data == Color.class) {
                Particles.show(player, particle, at, count, spread, spread, spread, speed, color);
            } else if (data == Particle.DustOptions.class) {
                Particles.show(player, particle, at, count, spread, spread, spread, speed,
                        new Particle.DustOptions(color, 1.0F));
            } else {
                unsupported(particle);
            }
        } catch (IllegalArgumentException invalid) {
            unsupported(particle);
        }
    }

    private void unsupported(Particle particle) {
        if (WARNED.add(particle)) {
            NexusLog.warn(LogTopic.KITS, "Particule ignorée dans les cérémonies : " + particle.name() + ", données "
                    + particle.getDataType().getSimpleName() + " non gérées");
        }
    }

    public void sound(Sound sound, float volume, float pitch) {
        try {
            player.playSound(anchor, sound, volume, Math.max(0.5F, Math.min(2.0F, pitch)));
        } catch (RuntimeException unavailable) {
            NexusLog.warn(LogTopic.KITS, "Son indisponible dans les cérémonies", unavailable);
        }
    }

    public void title(String main, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            player.showTitle(Title.title(Mini.label(main), Mini.label(subtitle),
                    Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L))));
        } catch (RuntimeException failure) {
            NexusLog.warn(LogTopic.KITS, "Titre de cérémonie non affiché", failure);
        }
    }

    public void claimTitle(String subtitle, int stay) {
        title(kit.name(), subtitle, 4, stay, 12);
    }

    public void firework(FireworkEffect.Type type, boolean trail, boolean flicker, double spread) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        Location at = anchor.clone().add((Math.random() - 0.5D) * spread, 1.2D, (Math.random() - 0.5D) * spread);
        Firework firework = world.spawn(at, Firework.class, spawned -> {
            spawned.setVisibleByDefault(false);
            spawned.setSilent(true);
            spawned.setPersistent(false);
            FireworkMeta meta = spawned.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(type)
                    .withColor(show.primary(), show.accent())
                    .withFade(Color.WHITE)
                    .trail(trail)
                    .flicker(flicker)
                    .build());
            meta.setPower(0);
            spawned.setFireworkMeta(meta);
        });
        reveal(firework);
        sound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.4F, 1.5F);
        Scheduling.regionLater(anchor, () -> {
            if (firework.isValid()) {
                firework.detonate();
            }
            sound(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6F, 1.2F);
        }, 2L);
    }

    void reveal(Entity display) {
        Scheduling.reveal(player, display, true);
    }

    void clear() {
        for (ItemDisplay display : displays) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        for (BlockDisplay display : blocks) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
        blocks.clear();
        pool.clear();
        applied.clear();
        core = null;
    }

    static Transformation zero() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf());
    }

    static Quaternionf rotation(double yaw, double pitch) {
        return new Quaternionf().rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch));
    }

    static Vector3f vector(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }
}
