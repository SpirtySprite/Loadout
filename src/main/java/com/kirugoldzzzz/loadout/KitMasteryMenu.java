package com.kirugoldzzzz.loadout;

import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.gui.ConfirmMenu;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class KitMasteryMenu {

    static final int ROWS = 5;
    static final Material[] TIER_ICONS = {Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT,
            Material.DIAMOND, Material.NETHERITE_INGOT, Material.NETHER_STAR, Material.DRAGON_EGG};

    private final KitActions actions;

    public KitMasteryMenu(KitActions actions) {
        this.actions = actions;
    }

    static int[] columns(int count) {
        int shown = Math.max(1, Math.min(7, count));
        int[] columns = new int[shown];
        int start = 5 - shown / 2;
        for (int index = 0; index < shown; index++) {
            columns[index] = start + index + (shown % 2 == 0 && index >= shown / 2 ? 1 : 0);
        }
        return columns;
    }

    public void open(Player player, Kit kit, Runnable back) {
        KitService service = actions.service();
        Kit current = service.kit(kit.id()).orElse(kit);
        KitViewer viewer = service.viewer(player);
        KitService.Progress progress = service.progress(viewer, current, System.currentTimeMillis());
        KitMastery mastery = progress.mastery();
        Runnable reopen = () -> open(player, current, back);
        Gui gui = Gui.builder().rows(ROWS).title(Mini.parse(Palette.smallTitle(Tr.t("Maîtrise du kit")))).create();
        Guis.fill(gui);
        gui.setItem(1, 5, header(current, progress));
        if (!mastery.enabled()) {
            gui.setItem(3, 5, Guis.display(Material.BARRIER, Palette.MUTED + Tr.t("<b>Pas de maîtrise</b>"),
                    Card.of(KitStyle.HEX).blank().line(Tr.t("Ce kit ne monte pas en palier.")).build()));
        }
        List<KitMastery.Tier> tiers = mastery.tiers();
        int[] columns = columns(tiers.size());
        for (int index = 0; index < Math.min(columns.length, tiers.size()); index++) {
            int level = index + 1;
            gui.setItem(3, columns[index], tier(player, current, mastery, tiers.get(index), level, progress, reopen));
        }
        if (back != null) {
            gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        }
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private GuiItem header(Kit kit, KitService.Progress progress) {
        Card card = Card.of(KitStyle.HEX).tag(Tr.t("Maîtrise")).blank()
                .line(Tr.t("Chaque récupération rapproche le kit"))
                .line(Tr.t("du palier suivant. Un palier atteint"))
                .line(Tr.t("reste acquis pour toujours."))
                .blank()
                .count(Card.AMOUNT, Tr.t("Récupérations"), progress.uses())
                .stat(Card.STAR, Tr.t("Palier actuel"), progress.level() + " / " + progress.mastery().tiers().size());
        if (progress.bonus() > 0) {
            card.stat(Palette.SUCCESS, "✚", Tr.t("Bonus actuel"), "+" + progress.bonus() + "%");
        }
        return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), kit.name(), card.build(), true),
                event -> event.setCancelled(true));
    }

    private GuiItem tier(Player player, Kit kit, KitMastery mastery, KitMastery.Tier tier, int level,
                         KitService.Progress progress, Runnable reopen) {
        boolean reached = progress.level() >= level;
        boolean next = progress.level() + 1 == level;
        Card card = Card.of(KitStyle.HEX).tag(Tr.t("Palier ") + level).blank();
        if (reached) {
            card.raw(Card.noteLine(Palette.SUCCESS, Palette.CHECK, Tr.t("Atteint")));
        } else if (next) {
            card.raw(Card.noteLine(Palette.WARNING, Card.TIME, Tr.t("Prochain palier")));
            card.raw(KitStyle.progress(mastery.progress(progress.uses(), progress.level())));
        } else {
            card.raw(Card.noteLine(Palette.MUTED, Palette.CROSS, Tr.t("Verrouillé")));
        }
        card.section(Tr.t("Pour l'atteindre"))
                .stat(Card.AMOUNT, Tr.t("Récupérations"), Math.min(progress.uses(), tier.claims()) + " / " + tier.claims());
        card.section(Tr.t("Avantages"));
        if (tier.cooldownReduction() > 0) {
            card.stat(Card.TIME, Tr.t("Recharge"), "-" + tier.cooldownReduction() + "%");
        }
        if (tier.bonus() > 0) {
            card.stat(Card.MONEY, Tr.t("Argent, points, niveaux"), "+" + tier.bonus() + "%");
        }
        if (tier.extraRolls() > 0) {
            card.stat(Card.CHANCE, Tr.t("Tirages aléatoires"), "+" + tier.extraRolls());
        }
        KitShow ceremony = KitShow.of(KitShow.levelFor(kit, level));
        card.stat(Palette.WARNING, "✦", Tr.t("Cérémonie"), ceremony.name());
        if (!tier.items().isEmpty()) {
            card.stat(Card.AMOUNT, Tr.t("Objets bonus"), tier.items().size() + Tr.t(" à chaque récupération"));
            for (ItemStack item : tier.items()) {
                card.raw(Palette.MUTED + "  " + item.getAmount() + "x " + KitStatistic.name(item.getType().name()));
            }
        }
        if (next && tier.purchasable()) {
            card.section(Tr.t("Débloquer maintenant"));
            if (tier.upgrade().money() > 0.0D) {
                card.money(Tr.t("Prix"), tier.upgrade().money());
            }
            if (tier.upgrade().shards() > 0L) {
                card.stat(Card.STAR, KitPoints.label(), Numbers.count(tier.upgrade().shards()));
            }
            if (tier.upgrade().levels() > 0) {
                card.stat(Card.STAR, Tr.t("Niveaux"), String.valueOf(tier.upgrade().levels()));
            }
            card.blank().click(Tr.t("pour acheter ce palier"));
        }
        Material icon = TIER_ICONS[Math.min(TIER_ICONS.length - 1, level - 1)];
        String name = (reached ? Palette.SUCCESS : next ? Palette.WARNING : Palette.MUTED) + "<b>" + tier.name() + "</b>";
        return new GuiItem(KitStyle.decorate(new ItemStack(reached || next ? icon : Material.GRAY_DYE), name,
                card.build(), reached), event -> {
            Player viewer = (Player) event.getWhoClicked();
            if (!next || !tier.purchasable()) {
                Guis.deny(viewer);
                return;
            }
            Guis.click(viewer);
            ConfirmMenu.create(Tr.t("Acheter un palier"))
                    .subject(KitStyle.decorate(new ItemStack(icon), name, List.of(), true))
                    .question(Tr.t("Passer le kit au palier ") + tier.name() + " ?")
                    .confirmLabel(Tr.t("Acheter"))
                    .details(card.build())
                    .onConfirm(confirmer -> {
                        actions.report(confirmer, actions.service().upgrade(confirmer, kit));
                        reopen.run();
                    })
                    .onCancel(confirmer -> reopen.run())
                    .open(viewer);
        });
    }
}
