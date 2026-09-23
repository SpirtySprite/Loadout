package com.kirugoldzzzz.loadout;

import com.foliagui.animation.GuiAnimation;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import net.kyori.adventure.text.Component;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KitPreviewMenu {

    static final int ROWS = 6;
    static final int[] ARMOUR_COLUMNS = {3, 4, 5, 6};
    static final int[] ARMOUR_SLOTS = {KitSlots.HELMET, KitSlots.CHESTPLATE, KitSlots.LEGGINGS, KitSlots.BOOTS};
    static final int OFFHAND_COLUMN = 7;
    static final long REFRESH_TICKS = 20L;

    private final KitActions actions;
    private KitMasteryMenu mastery;

    public KitPreviewMenu(KitActions actions) {
        this.actions = actions;
    }

    public void bind(KitMasteryMenu masteryMenu) {
        this.mastery = masteryMenu;
    }

    public void open(Player player, Kit kit, Runnable back) {
        long started = System.nanoTime();
        Gui gui = Gui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.smallTitle("Aperçu du kit")))
                .create();
        Guis.fill(gui);
        for (int index = 0; index < ARMOUR_SLOTS.length; index++) {
            gui.setItem(1, ARMOUR_COLUMNS[index], slot(kit, ARMOUR_SLOTS[index]));
        }
        gui.setItem(1, OFFHAND_COLUMN, slot(kit, KitSlots.OFFHAND));
        for (int slot = KitSlots.HOTBAR; slot < KitSlots.STORAGE; slot++) {
            int index = slot - KitSlots.HOTBAR;
            gui.setItem(Guis.slot(2 + index / 9, 1 + index % 9), slot(kit, slot));
        }
        for (int slot = 0; slot < KitSlots.HOTBAR; slot++) {
            gui.setItem(Guis.slot(5, 1 + slot), slot(kit, slot));
        }
        gui.setItem(1, 1, rewards(kit));
        if (!kit.pool().empty()) {
            gui.setItem(1, 9, poolButton(kit, back));
        }
        if (back != null) {
            gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        }
        boolean armour = false;
        for (int slot : ARMOUR_SLOTS) {
            armour |= kit.contents().containsKey(slot);
        }
        if (armour) {
            gui.setItem(ROWS, 3, tryOnButton(kit));
        }
        if (mastery != null && actions.service().catalog().masteryOf(kit).enabled()) {
            gui.setItem(1, 8, masteryButton(player, kit, back));
        }
        render(gui, player, kit, back);
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
        GuiAnimation.play(gui, player, REFRESH_TICKS, animated -> render(gui, player, kit, back));
        Guis.opened(started);
    }

    private void render(Gui gui, Player player, Kit kit, Runnable back) {
        Kit current = actions.service().kit(kit.id()).orElse(kit);
        KitStatus status = actions.service().status(player, current);
        gui.updateItem(Guis.slot(ROWS, 5), claimButton(gui, player, current, status, back));
        if (current.options().giftable()) {
            gui.updateItem(Guis.slot(ROWS, 7), giftButton(current, back));
        }
    }

    private GuiItem slot(Kit kit, int slot) {
        ItemStack item = kit.contents().get(slot);
        if (item == null) {
            return Guis.display(KitSlots.equipment(slot) ? Material.GRAY_STAINED_GLASS_PANE
                            : Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    Palette.MUTED + KitSlots.label(slot), List.of(Palette.MUTED + "Vide"));
        }
        ItemStack shown = item.clone();
        ItemMeta meta = shown.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Mini.label(Card.noteLine(Palette.SECONDARY, Card.FLAG, KitSlots.label(slot))));
            if (KitSlots.equipment(slot) && kit.options().autoEquip()) {
                lore.add(Mini.label(Card.noteLine(Palette.SUCCESS, Palette.CHECK, "Équipé automatiquement si libre")));
            }
            if (kit.options().protectItems()) {
                lore.add(Mini.label(Card.noteLine(Palette.WARNING, "⛓", "Invendable")));
            }
            meta.lore(lore);
            shown.setItemMeta(meta);
        }
        return new GuiItem(shown, event -> event.setCancelled(true));
    }

    private GuiItem rewards(Kit kit) {
        Card card = Card.of(KitStyle.HEX).tag("Kit");
        if (!kit.description().isEmpty()) {
            card.blank();
            kit.description().forEach(card::line);
        }
        KitStyle.contents(card, kit, actions::crateName);
        return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), kit.name(), card.build(), true),
                event -> event.setCancelled(true));
    }

    private GuiItem poolButton(Kit kit, Runnable back) {
        KitPool pool = kit.pool();
        Card card = Card.of(KitStyle.HEX).tag("Tirage aléatoire").blank()
                .stat(Card.CHANCE, "Tirages", pool.effectiveRolls() + (pool.unique() ? " sans doublon" : " avec doublons possibles"))
                .count(Card.AMOUNT, "Possibilités", pool.entries().size())
                .blank()
                .click("pour voir les chances");
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.ENDER_EYE), Palette.heading("Tirage aléatoire"),
                card.build(), true), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            openPool(viewer, kit, () -> open(viewer, kit, back));
        });
    }

    public void openPool(Player player, Kit kit, Runnable back) {
        Gui gui = Gui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.smallTitle("Tirage du kit")))
                .create();
        Guis.fill(gui);
        KitPool pool = kit.pool();
        List<KitPool.Entry> entries = pool.entries();
        int shown = Math.min(entries.size(), 45);
        for (int index = 0; index < shown; index++) {
            KitPool.Entry entry = entries.get(index);
            gui.setItem(index, poolEntry(pool, entry));
        }
        gui.setItem(ROWS, 5, Guis.display(Material.ENDER_EYE, Palette.heading("Comment ça marche"),
                Card.of(KitStyle.HEX).blank()
                        .line(pool.effectiveRolls() + " objet(s) tiré(s) à chaque récupération.")
                        .line(pool.unique() ? "Un même objet ne sort qu'une fois." : "Un objet peut sortir plusieurs fois.")
                        .line(pool.exact() ? "Les pourcentages sont exacts." : "Part de chaque objet dans un tirage.")
                        .build()));
        if (back != null) {
            gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        }
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private GuiItem poolEntry(KitPool pool, KitPool.Entry entry) {
        ItemStack base = entry.item() == null ? new ItemStack(Material.BARRIER) : entry.item().clone();
        base.setAmount(Math.max(1, Math.min(base.getMaxStackSize(), entry.minimum())));
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            String label = pool.exact() ? "Chance d'obtention" : "Part du tirage";
            double value = pool.exact() ? pool.chance(entry) : pool.share(entry);
            lore.add(Mini.label(Card.noteLine(Palette.WARNING, Card.CHANCE, label + " : " + Palette.WARNING + percent(value))));
            String amount = entry.minimum() == entry.maximum() ? String.valueOf(entry.minimum())
                    : entry.minimum() + " à " + entry.maximum();
            lore.add(Mini.label(Card.noteLine(Palette.SECONDARY, Card.AMOUNT, "Quantité : " + Palette.SECONDARY + amount)));
            meta.lore(lore);
            base.setItemMeta(meta);
        }
        return new GuiItem(base, event -> event.setCancelled(true));
    }

    static String percent(double value) {
        double percent = value * 100.0D;
        if (percent >= 10.0D) {
            return String.format(Locale.FRANCE, "%.1f %%", percent);
        }
        return String.format(Locale.FRANCE, "%.2f %%", percent);
    }

    private GuiItem claimButton(Gui gui, Player player, Kit kit, KitStatus status, Runnable back) {
        Card card = Card.of(KitStyle.HEX).tag("Récupération").blank().raw(KitStyle.stateLine(status,
                System.currentTimeMillis()));
        KitStyle.conditions(card, kit, status, actions.service().settings().zone());
        KitStyle.limits(card, kit, status, actions.service().settings().zone());
        card.blank();
        int discount = actions.service().discount(kit, System.currentTimeMillis());
        if (discount > 0) {
            card.raw(Palette.WARNING + "⚡ Kit du jour " + Palette.SUCCESS + "-" + discount + "%");
        }
        if (status.available()) {
            card.click(kit.cost().free() ? "pour récupérer le kit" : "pour acheter et récupérer");
        } else {
            card.deny("Pas encore disponible");
        }
        Material icon = status.available() ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        if (status.state() == KitStatus.State.COOLDOWN) {
            icon = Material.ORANGE_CONCRETE;
        }
        return new GuiItem(KitStyle.decorate(new ItemStack(icon),
                (status.available() ? Palette.SUCCESS : Palette.ERROR) + "<b>" + Card.small("Récupérer") + "</b>",
                card.build(), status.available()), event -> {
            Player viewer = (Player) event.getWhoClicked();
            actions.request(viewer, kit, KitService.Source.MENU, () -> {
                if (gui.isOpenFor(viewer)) {
                    render(gui, viewer, kit, back);
                } else {
                    open(viewer, kit, back);
                }
            });
        });
    }

    private GuiItem tryOnButton(Kit kit) {
        List<String> lore = Card.of(KitStyle.HEX).tag("Essayage").blank()
                .line("Un mannequin holographique porte")
                .line("l'armure du kit devant vous")
                .line("pendant 10 secondes.")
                .blank()
                .click("pour essayer")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.ARMOR_STAND), Palette.heading("Essayer l'armure"),
                lore, false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            viewer.closeInventory();
            KitTryOn.show(viewer, kit);
        });
    }

    private GuiItem masteryButton(Player player, Kit kit, Runnable back) {
        KitService.Progress progress = actions.service().progress(actions.service().viewer(player), kit,
                System.currentTimeMillis());
        KitMastery.Tier tier = progress.mastery().tier(progress.level());
        Card card = Card.of(KitStyle.HEX).tag("Maîtrise").blank()
                .stat(Card.STAR, "Palier", tier == null ? "aucun" : tier.name())
                .count(Card.AMOUNT, "Récupérations", progress.uses());
        KitMastery.Tier next = progress.mastery().next(progress.level());
        if (next != null) {
            card.raw(KitStyle.progress(progress.mastery().progress(progress.uses(), progress.level())));
        }
        card.blank().click("pour voir les paliers");
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.EXPERIENCE_BOTTLE), Palette.heading("Maîtrise"),
                card.build(), progress.level() > 0), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            mastery.open(viewer, kit, () -> open(viewer, kit, back));
        });
    }

    private GuiItem giftButton(Kit kit, Runnable back) {
        List<String> lore = Card.of(KitStyle.HEX).tag("Cadeau").blank()
                .line("Payez ce kit pour un autre joueur,")
                .line("il reçoit un bon à ouvrir.")
                .blank()
                .click("pour choisir le joueur")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.CAKE), Palette.heading("Offrir ce kit"), lore,
                false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            actions.promptGift(viewer, kit, () -> open(viewer, kit, back));
        });
    }
}
