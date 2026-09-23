package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.effect.Particles;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Mini;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KitTryOn {

    static final int DURATION = 200;
    static final double DISTANCE = 2.6D;
    static final float SPIN = 4.0F;
    private static final EquipmentSlot[] LOCKED = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.FEET,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final Set<KitTryOn> RUNNING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, KitTryOn> BY_PLAYER = new ConcurrentHashMap<>();

    private final Player player;
    private final Kit kit;
    private final Location anchor;
    private final AtomicBoolean finished = new AtomicBoolean();
    private ArmorStand stand;
    private volatile ScheduledTask task;
    private int tick;
    private float yaw;

    private KitTryOn(Player player, Kit kit) {
        this.player = player;
        this.kit = kit;
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().setY(0.0D);
        if (direction.lengthSquared() < 1.0E-4D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        direction.normalize().multiply(DISTANCE);
        Location base = player.getLocation().clone().add(direction);
        base.setPitch(0.0F);
        base.setYaw(player.getLocation().getYaw() + 180.0F);
        this.anchor = base;
        this.yaw = base.getYaw();
    }

    public static void show(Player player, Kit kit) {
        KitTryOn previous = BY_PLAYER.remove(player.getUniqueId());
        if (previous != null) {
            previous.finish();
        }
        KitTryOn tryOn = new KitTryOn(player, kit);
        BY_PLAYER.put(player.getUniqueId(), tryOn);
        tryOn.start();
    }

    public static void stopAll() {
        for (KitTryOn running : List.copyOf(RUNNING)) {
            running.finish();
        }
    }

    private void start() {
        if (anchor.getWorld() == null) {
            return;
        }
        RUNNING.add(this);
        task = Scheduling.regionTimer(anchor, this::step, 1L, 1L);
        if (task == null) {
            finish();
        }
    }

    private void step() {
        if (!player.isOnline() || !player.getWorld().equals(anchor.getWorld())
                || player.getLocation().distanceSquared(anchor) > 144.0D) {
            finish();
            return;
        }
        if (tick == 0) {
            spawn();
        } else if (stand != null && stand.isValid()) {
            yaw += SPIN;
            stand.setRotation(yaw, 0.0F);
            if (tick % 10 == 0) {
                Particles.show(player, Particle.END_ROD, anchor.clone().add(0.0D, 1.1D, 0.0D), 3, 0.35D, 0.6D, 0.35D,
                        0.01D);
            }
        }
        if (tick >= DURATION) {
            Particles.show(player, Particle.CLOUD, anchor.clone().add(0.0D, 1.0D, 0.0D), 20, 0.3D, 0.6D, 0.3D, 0.02D);
            player.playSound(anchor, Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6F, 0.8F);
            finish();
            return;
        }
        tick++;
    }

    private void spawn() {
        stand = anchor.getWorld().spawn(anchor, ArmorStand.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setBasePlate(false);
            spawned.setArms(true);
            spawned.setSilent(true);
            spawned.setCanPickupItems(false);
            spawned.setRightArmPose(new EulerAngle(Math.toRadians(-20.0D), 0.0D, Math.toRadians(-10.0D)));
            spawned.setLeftArmPose(new EulerAngle(Math.toRadians(-20.0D), 0.0D, Math.toRadians(10.0D)));
            spawned.customName(Mini.label(kit.name()));
            spawned.setCustomNameVisible(true);
            for (EquipmentSlot slot : LOCKED) {
                spawned.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                spawned.addEquipmentLock(slot, ArmorStand.LockType.ADDING);
            }
            EntityEquipment equipment = spawned.getEquipment();
            equipment.setHelmet(copy(KitSlots.HELMET));
            equipment.setChestplate(copy(KitSlots.CHESTPLATE));
            equipment.setLeggings(copy(KitSlots.LEGGINGS));
            equipment.setBoots(copy(KitSlots.BOOTS));
            equipment.setItemInOffHand(copy(KitSlots.OFFHAND));
            equipment.setItemInMainHand(copy(0));
        });
        Scheduling.reveal(player, stand, true);
        Particles.show(player, Particle.TOTEM_OF_UNDYING, anchor.clone().add(0.0D, 1.0D, 0.0D), 30, 0.3D, 0.6D, 0.3D,
                0.2D);
        player.playSound(anchor, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8F, 1.0F);
    }

    private ItemStack copy(int slot) {
        ItemStack item = kit.contents().get(slot);
        return item == null ? null : item.clone();
    }

    private void finish() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        RUNNING.remove(this);
        BY_PLAYER.remove(player.getUniqueId(), this);
        ScheduledTask running = task;
        if (running != null) {
            running.cancel();
        }
        Runnable clear = () -> {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
            stand = null;
        };
        if (Bukkit.isOwnedByCurrentRegion(anchor)) {
            clear.run();
        } else {
            Scheduling.region(anchor, clear);
        }
    }
}
