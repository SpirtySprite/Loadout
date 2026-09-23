package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.effect.Particles;
import com.foliagui.animation.GuiAnimation;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.foliagui.scheduler.TaskHandle;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KitRoulette {

    static final int REEL = 9;
    static final int CENTER = 4;
    static final int STEPS = 30;
    static final int HOLD = 16;
    static final int FINAL_HOLD = 50;
    static final int ANIMATED_LIMIT = 5;
    private static final Material[][] FRAMES_BY_LEVEL = {
            {Material.WHITE_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE},
            {Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE},
            {Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    Material.BLUE_STAINED_GLASS_PANE},
            {Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
                    Material.LIME_STAINED_GLASS_PANE},
            {Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
                    Material.MAGENTA_STAINED_GLASS_PANE}};

    private KitRoulette() {
    }

    static int[] schedule(int steps) {
        int[] stamps = new int[steps];
        int tick = 0;
        for (int step = 0; step < steps; step++) {
            double progress = step / (double) Math.max(1, steps - 1);
            tick += 1 + (int) Math.round(progress * progress * progress * 5.0D);
            stamps[step] = tick;
        }
        return stamps;
    }

    public static void open(KitService service, Player player, Kit kit, List<KitPool.Pull> pulls) {
        KitService.Progress progress = service.progress(service.viewer(player), kit, System.currentTimeMillis());
        KitShow show = KitShow.of(KitShow.levelFor(kit, progress.level()));
        KitPool pool = kit.pool();
        List<KitPool.Entry> entries = pool.entries();
        if (pulls.isEmpty() || entries.isEmpty()) {
            service.finishRoulette(player);
            return;
        }
        Gui gui = Gui.builder()
                .rows(5)
                .title(Mini.parse(Palette.smallTitle("Roulette mystère")))
                .create();
        Guis.fill(gui);
        AtomicBoolean finished = new AtomicBoolean();
        Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) {
                service.finishRoulette(player);
            }
        };
        AtomicBoolean closed = new AtomicBoolean();
        gui.setCloseAction(event -> {
            closed.set(true);
            finish.run();
        });
        int animated = Math.min(ANIMATED_LIMIT, pulls.size());
        List<List<ItemStack>> reels = new ArrayList<>();
        for (int index = 0; index < animated; index++) {
            reels.add(reel(entries, pulls.get(index)));
        }
        int[] stamps = schedule(STEPS);
        int[] frame = {0};
        int[] pull = {0};
        int[] step = {0};
        int[] holdUntil = {-1};
        List<ItemStack> won = new ArrayList<>();
        gui.setItem(2, 5, pointer(true));
        gui.setItem(4, 5, pointer(false));
        gui.setItem(5, 5, header(kit, 0, pulls.size()));
        gui.open(player);
        TaskHandle[] handle = new TaskHandle[1];
        handle[0] = GuiAnimation.play(gui, player, 1L, ignored -> {
            int now = frame[0]++;
            if (closed.get()) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                return;
            }
            if (now % 4 == 0) {
                border(gui, now / 4, show);
            }
            if (pull[0] >= animated) {
                if (holdUntil[0] < 0) {
                    reveal(gui, pulls, won);
                    holdUntil[0] = now + FINAL_HOLD;
                    player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.3F);
                    player.playSound(player, show.burst(), 0.7F, show.pitch());
                    Particles.show(player, Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D),
                            20 + show.level() * 12, 0.4D, 0.6D, 0.4D, 0.25D);
                    finish.run();
                    Messages.send(player, "kits.roulette-done", KitService.kitResolver(kit),
                            Mini.value("amount", String.valueOf(pulls.size())));
                } else if (now >= holdUntil[0]) {
                    if (handle[0] != null) {
                        handle[0].cancel();
                    }
                    player.closeInventory();
                }
                return;
            }
            if (holdUntil[0] >= 0) {
                if (now >= holdUntil[0]) {
                    holdUntil[0] = -1;
                    pull[0]++;
                    step[0] = 0;
                    frame[0] = 0;
                    gui.updateItem(Guis.slot(5, 5), header(kit, Math.min(pull[0], pulls.size()), pulls.size()));
                }
                return;
            }
            List<ItemStack> reel = reels.get(pull[0]);
            boolean moved = false;
            while (step[0] < STEPS && now >= stamps[step[0]]) {
                step[0]++;
                moved = true;
            }
            if (moved) {
                draw(gui, reel, step[0]);
                float pitch = 0.8F + 1.0F * step[0] / STEPS;
                player.playSound(player, Sound.UI_BUTTON_CLICK, 0.35F, pitch);
            }
            if (step[0] >= STEPS) {
                KitPool.Pull landed = pulls.get(pull[0]);
                won.add(display(landed));
                ItemStack center = display(landed);
                gui.updateItem(Guis.slot(3, 1 + CENTER), new GuiItem(KitStyle.decorate(center,
                        Palette.SUCCESS + "<b>" + Card.small("Gagné") + "</b>", Card.of(KitStyle.HEX).blank()
                                .stat(Card.AMOUNT, "Quantité", landed.amount()).build(), true),
                        event -> event.setCancelled(true)));
                player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.5F + pull[0] * 0.1F);
                holdUntil[0] = now + HOLD;
            }
        });
    }

    static List<ItemStack> reel(List<KitPool.Entry> entries, KitPool.Pull target) {
        List<ItemStack> reel = new ArrayList<>();
        int total = STEPS + REEL;
        int landing = STEPS + CENTER;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int weights = 0;
        for (KitPool.Entry entry : entries) {
            weights += entry.weight();
        }
        for (int index = 0; index < total; index++) {
            if (index == landing) {
                reel.add(display(target));
                continue;
            }
            int roll = random.nextInt(Math.max(1, weights));
            KitPool.Entry picked = entries.getLast();
            for (KitPool.Entry entry : entries) {
                roll -= entry.weight();
                if (roll < 0) {
                    picked = entry;
                    break;
                }
            }
            reel.add(display(new KitPool.Pull(picked, picked.minimum())));
        }
        return reel;
    }

    static ItemStack display(KitPool.Pull pull) {
        ItemStack base = pull.entry().item() == null ? new ItemStack(Material.BARRIER) : pull.entry().item().clone();
        base.setAmount(Math.max(1, Math.min(base.getMaxStackSize(), pull.amount())));
        return base;
    }

    private static void draw(Gui gui, List<ItemStack> reel, int offset) {
        for (int column = 0; column < REEL; column++) {
            int index = offset + column;
            ItemStack item = index < reel.size() ? reel.get(index) : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            gui.updateItem(Guis.slot(3, 1 + column), new GuiItem(item, event -> event.setCancelled(true)));
        }
    }

    private static void reveal(Gui gui, List<KitPool.Pull> pulls, List<ItemStack> won) {
        for (int column = 0; column < REEL; column++) {
            gui.updateItem(Guis.slot(3, 1 + column), Guis.filler());
        }
        int shown = Math.min(REEL, pulls.size());
        int start = 1 + (REEL - shown) / 2;
        for (int index = 0; index < shown; index++) {
            ItemStack item = display(pulls.get(index));
            gui.updateItem(Guis.slot(3, start + index), new GuiItem(KitStyle.decorate(item,
                    Palette.SUCCESS + "<b>" + Card.small("Obtenu") + "</b>", Card.of(KitStyle.HEX).blank()
                            .stat(Card.AMOUNT, "Quantité", pulls.get(index).amount())
                            .line("Déjà dans votre inventaire")
                            .build(), true), event -> event.setCancelled(true)));
        }
        won.clear();
    }

    private static void border(Gui gui, int phase, KitShow show) {
        Material[] frames = FRAMES_BY_LEVEL[Math.max(0, Math.min(FRAMES_BY_LEVEL.length - 1, show.level()))];
        for (int column = 1; column <= 9; column++) {
            Material material = frames[Math.floorMod(phase + column, frames.length)];
            ItemStack pane = KitStyle.decorate(new ItemStack(material), " ", List.of(), false);
            gui.updateItem(Guis.slot(1, column), new GuiItem(pane, event -> event.setCancelled(true)));
            if (column != 5) {
                gui.updateItem(Guis.slot(5, column), new GuiItem(pane, event -> event.setCancelled(true)));
                gui.updateItem(Guis.slot(2, column), new GuiItem(pane, event -> event.setCancelled(true)));
                gui.updateItem(Guis.slot(4, column), new GuiItem(pane, event -> event.setCancelled(true)));
            }
        }
    }

    private static GuiItem pointer(boolean top) {
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.HOPPER), Palette.WARNING + (top ? "▼" : "▲"),
                List.of(), true), event -> event.setCancelled(true));
    }

    private static GuiItem header(Kit kit, int done, int total) {
        return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), kit.name(), Card.of(KitStyle.HEX).tag("Roulette")
                .blank()
                .stat(Card.CHANCE, "Tirages", done + " / " + total)
                .line("Fermer le menu vous donne")
                .line("immédiatement les objets.")
                .build(), true), event -> event.setCancelled(true));
    }
}
