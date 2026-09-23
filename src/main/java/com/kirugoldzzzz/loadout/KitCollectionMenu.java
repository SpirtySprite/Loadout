package com.kirugoldzzzz.loadout;

import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KitCollectionMenu {

    static final int ROWS = 6;
    static final int PAGE = 36;

    private final KitActions actions;
    private final KitPreviewMenu preview;

    public KitCollectionMenu(KitActions actions, KitPreviewMenu preview) {
        this.actions = actions;
        this.preview = preview;
    }

    public void open(Player player, Runnable back) {
        open(player, 0, back);
    }

    private void open(Player player, int page, Runnable back) {
        KitService service = actions.service();
        KitCatalog catalog = service.catalog();
        Set<String> collected = service.collected(player.getUniqueId());
        Set<String> marks = service.repository().milestones(player.getUniqueId());
        KitViewer viewer = service.viewer(player);
        long now = System.currentTimeMillis();
        Gui gui = Gui.builder().rows(ROWS).title(Mini.parse(Palette.smallTitle(Tr.t("Collection de kits")))).create();
        Guis.fill(gui);
        List<Kit> kits = new ArrayList<>(catalog.kits().values());
        int pages = Math.max(1, (kits.size() + PAGE - 1) / PAGE);
        int safe = Math.max(0, Math.min(page, pages - 1));
        for (int index = 0; index < PAGE && safe * PAGE + index < kits.size(); index++) {
            Kit kit = kits.get(safe * PAGE + index);
            gui.setItem(index, entry(player, viewer, kit, collected.contains(kit.id()), now, back, safe));
        }
        int total = catalog.kits().size();
        int owned = collected.size();
        int masteredCount = 0;
        int bestStreak = 0;
        for (Kit kit : catalog.kits().values()) {
            KitService.Progress progress = service.progress(viewer, kit, now);
            if (progress.mastery().maxed(progress.level())) {
                masteredCount++;
            }
            bestStreak = Math.max(bestStreak, progress.best());
        }
        Card summary = Card.of(KitStyle.HEX).tag(Tr.t("Collection")).blank()
                .stat(Card.CATEGORY, Tr.t("Kits découverts"), owned + " / " + total)
                .raw(KitStyle.progress(total == 0 ? 0.0D : owned / (double) total))
                .blank()
                .count(Card.STAR, Tr.t("Kits maîtrisés"), masteredCount)
                .count("✹", Tr.t("Meilleure série"), bestStreak);
        gui.setItem(5, 5, new GuiItem(KitStyle.decorate(new ItemStack(Material.KNOWLEDGE_BOOK),
                Palette.title(Tr.t("Votre collection")), summary.build(), owned == total && total > 0),
                event -> event.setCancelled(true)));
        Map<Integer, KitRewards> milestones = catalog.progression().collection();
        int column = 1;
        for (Map.Entry<Integer, KitRewards> milestone : milestones.entrySet()) {
            if (column > 9) {
                break;
            }
            if (column == 5) {
                column++;
            }
            gui.setItem(5, column++, milestone(milestone.getKey(), milestone.getValue(), owned,
                    marks.contains(KitService.COLLECTION_MARK + milestone.getKey())));
        }
        if (back != null) {
            gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        }
        if (safe > 0) {
            gui.setItem(ROWS, Guis.PREVIOUS_SLOT, Guis.button(Material.ARROW, Palette.ACCENT + Palette.BACK
                    + Tr.t(" Page précédente"), List.of(), viewer2 -> open(viewer2, safe - 1, back)));
        }
        if (safe < pages - 1) {
            gui.setItem(ROWS, Guis.NEXT_SLOT, Guis.button(Material.ARROW, Palette.ACCENT + Tr.t("Page suivante ")
                    + Palette.POINTER, List.of(), viewer2 -> open(viewer2, safe + 1, back)));
        }
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private GuiItem entry(Player player, KitViewer viewer, Kit kit, boolean collected, long now, Runnable back,
                          int page) {
        KitService service = actions.service();
        KitService.Progress progress = service.progress(viewer, kit, now);
        Card card = Card.of(KitStyle.HEX).tag(collected ? Tr.t("Découvert") : Tr.t("À découvrir")).blank();
        if (collected) {
            card.count(Card.AMOUNT, Tr.t("Récupérations"), progress.uses());
            if (progress.mastery().enabled()) {
                KitMastery.Tier tier = progress.mastery().tier(progress.level());
                card.stat(Card.STAR, Tr.t("Maîtrise"), tier == null ? Tr.t("aucun palier") : tier.name());
            }
            if (progress.best() > 0) {
                card.count("✹", Tr.t("Meilleure série"), progress.best());
            }
        } else {
            card.line(Tr.t("Récupérez ce kit une fois pour"))
                    .line(Tr.t("l'ajouter à votre collection."));
        }
        card.blank().click(Tr.t("pour voir le kit"));
        ItemStack icon = collected ? KitStyle.icon(kit) : new ItemStack(Material.GRAY_DYE);
        String name;
        if (collected) {
            name = kit.name();
        } else {
            name = kit.hideLocked() ? Palette.MUTED + "<b>???</b>" : Palette.MUTED + Mini.plain(Mini.label(kit.name()));
        }
        boolean mastered = progress.mastery().maxed(progress.level());
        return new GuiItem(KitStyle.decorate(icon, name, card.build(), mastered), event -> {
            Player clicker = (Player) event.getWhoClicked();
            Guis.click(clicker);
            preview.open(clicker, kit, () -> open(clicker, page, back));
        });
    }

    private GuiItem milestone(int count, KitRewards rewards, int owned, boolean claimed) {
        Card card = Card.of(KitStyle.HEX).tag(Tr.t("Palier de collection")).blank()
                .stat(Card.CATEGORY, Tr.t("Kits à découvrir"), Math.min(owned, count) + " / " + count);
        if (rewards.money() > 0.0D) {
            card.money(Tr.t("Argent"), rewards.money());
        }
        if (rewards.shards() > 0L) {
            card.stat(Card.STAR, Tr.t("Fragments"), Numbers.count(rewards.shards()));
        }
        if (rewards.levels() > 0) {
            card.stat(Card.STAR, Tr.t("Niveaux"), "+" + rewards.levels());
        }
        rewards.lines().forEach(line -> card.stat(Card.CALL, Tr.t("Bonus"), line));
        card.blank().raw(claimed ? Card.noteLine(Palette.SUCCESS, Palette.CHECK, Tr.t("Récompense obtenue"))
                : Card.noteLine(Palette.MUTED, Card.TIME, Tr.t("Obtenue automatiquement")));
        Material icon = claimed ? Material.LIME_CANDLE : owned >= count ? Material.YELLOW_CANDLE : Material.GRAY_CANDLE;
        return Guis.display(icon, (claimed ? Palette.SUCCESS : Palette.WARNING) + "<b>" + count + " kits</b>",
                card.build());
    }
}
