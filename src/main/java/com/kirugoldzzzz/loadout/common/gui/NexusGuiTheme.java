package com.kirugoldzzzz.loadout.common.gui;

import com.foliagui.gui.GuiTheme;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiAction;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

final class NexusGuiTheme {

    private NexusGuiTheme() {
    }

    static GuiTheme create() {
        return new GuiTheme()
                .filler(Guis::filler)
                .border(Guis::filler)
                .previousButtonItem(gui -> arrow(gui, true))
                .nextButtonItem(gui -> arrow(gui, false))
                .pageIndicator(Guis::pageIndicator)
                .clickSound(null)
                .successSound(null)
                .denySound(null)
                .pageSound(null);
    }

    private static GuiItem arrow(PaginatedGui gui, boolean previous) {
        GuiItem item = new GuiItem(Guis.skin(Material.ARROW, Card.title(Palette.PRIMARY_HEX, Palette.SECONDARY_HEX,
                        Card.small(previous ? Tr.t("Page précédente") : Tr.t("Page suivante"))),
                Card.of(Palette.PRIMARY_HEX).tag(Tr.t("Navigation"))
                        .stat(Card.FLAG, Tr.t("Page actuelle"), gui.getCurrentPage())
                        .stat(Card.AMOUNT, Tr.t("Pages au total"), gui.getPagesCount())
                        .blank()
                        .click(previous ? Tr.t("pour reculer d'une page") : Tr.t("pour avancer d'une page"))
                        .build(), false));
        GuiAction<InventoryClickEvent> sound = event -> Guis.click((Player) event.getWhoClicked());
        return item.onLeftClick(sound).onRightClick(sound).onShiftClick(sound);
    }
}
