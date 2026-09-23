package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Tr;

import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.NexusLog;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KitUnboxing {

    static final double DISTANCE = 2.7D;
    static final double DROP = 0.55D;
    static final double MAX_DISTANCE_SQUARED = 400.0D;
    static final int FAILURE_LIMIT = 3;
    private static final Set<KitUnboxing> RUNNING = ConcurrentHashMap.newKeySet();

    private final Player player;
    private final Location anchor;
    private final KitStage stage;
    private final KitCeremony ceremony;
    private final AtomicBoolean finished = new AtomicBoolean();

    private int tick;
    private int failures;
    private volatile ScheduledTask task;

    private KitUnboxing(Player player, Kit kit, List<ItemStack> contents, KitShow show) {
        this.player = player;
        this.anchor = anchorFor(player);
        List<ItemStack> shown = new ArrayList<>();
        for (ItemStack item : contents) {
            if (shown.size() >= show.items()) {
                break;
            }
            if (item != null && !item.getType().isAir()) {
                ItemStack single = item.clone();
                single.setAmount(1);
                shown.add(single);
            }
        }
        ItemStack icon = kit.icon() == null ? new ItemStack(Material.CHEST) : KitStyle.icon(kit);
        this.stage = new KitStage(player, kit, show, anchor, shown, pick(shown), icon);
        this.ceremony = KitCeremony.of(show);
    }

    public static void play(KitService.Claimed claimed) {
        play(claimed.player(), claimed.kit(), claimed.items(),
                KitShow.of(KitShow.levelFor(claimed.kit(), claimed.masteryLevel())));
    }

    public static void play(Player player, Kit kit, List<ItemStack> items, KitShow show) {
        new KitUnboxing(player, kit, items, show).start();
    }

    public static void stopAll() {
        for (KitUnboxing running : List.copyOf(RUNNING)) {
            running.finish();
        }
    }

    static ItemStack pick(List<ItemStack> items) {
        ItemStack best = null;
        int score = Integer.MIN_VALUE;
        for (ItemStack item : items) {
            int value = value(item);
            if (value > score) {
                score = value;
                best = item;
            }
        }
        return best;
    }

    static int value(ItemStack item) {
        int score = 64 - Math.min(64, item.getMaxStackSize());
        if (item.hasItemMeta()) {
            score += 20;
            score += 10 * item.getEnchantments().size();
        }
        return score;
    }

    static Location anchorFor(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().setY(0.0D);
        if (direction.lengthSquared() < 1.0E-4D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        direction.normalize().multiply(DISTANCE);
        Location anchor = eye.clone().add(direction);
        anchor.setY(eye.getY() - DROP);
        anchor.setYaw(0.0F);
        anchor.setPitch(0.0F);
        return anchor;
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
        if (!player.isOnline()) {
            finish();
            return;
        }
        if (away()) {
            finish();
            return;
        }
        try {
            ceremony.tick(stage, tick);
        } catch (RuntimeException failure) {
            failures++;
            NexusLog.warn(LogTopic.KITS, Tr.t("Cérémonie ") + ceremony.getClass().getSimpleName() + Tr.t(" interrompue au tick ")
                    + tick + Tr.t(" pour ") + player.getName(), failure);
            if (failures >= FAILURE_LIMIT) {
                finish();
                return;
            }
        }
        if (tick >= ceremony.duration()) {
            finish();
            return;
        }
        tick++;
    }

    private boolean away() {
        try {
            Location location = player.getLocation();
            return location.getWorld() == null || !location.getWorld().equals(anchor.getWorld())
                    || location.distanceSquared(anchor) > MAX_DISTANCE_SQUARED;
        } catch (RuntimeException unreachable) {
            return false;
        }
    }

    private void finish() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        RUNNING.remove(this);
        ScheduledTask running = task;
        if (running != null) {
            running.cancel();
        }
        if (Bukkit.isOwnedByCurrentRegion(anchor)) {
            stage.clear();
        } else {
            Scheduling.region(anchor, stage::clear);
        }
    }
}
