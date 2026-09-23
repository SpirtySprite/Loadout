package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Tr;

import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class KitListMenu {

    static final int ROWS = 6;
    static final int LIMIT = 45;

    record Spec(String title, List<String> help, String prompt, boolean longInput, Supplier<List<String>> values,
                Consumer<List<String>> save, Function<String, String> validate, Material icon) {
    }

    private KitListMenu() {
    }

    static void open(Player player, Spec spec, Runnable back) {
        Gui gui = Gui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.smallTitle(spec.title())))
                .create();
        Guis.fill(gui);
        List<String> values = new ArrayList<>(spec.values().get());
        Runnable reopen = () -> open(player, spec, back);
        for (int index = 0; index < Math.min(LIMIT, values.size()); index++) {
            gui.setItem(index, entry(spec, values, index, reopen));
        }
        gui.setItem(ROWS, 3, Guis.button(Material.LIME_DYE, Palette.SUCCESS + "<b>Ajouter</b>",
                Card.of(KitStyle.HEX).blank().line(spec.prompt()).blank().click(Tr.t("pour écrire une ligne")).build(),
                viewer -> {
                    if (values.size() >= LIMIT) {
                        Guis.deny(viewer);
                        return;
                    }
                    KitPrompts.text(viewer, spec.prompt(), spec.longInput(), typed -> {
                        String error = spec.validate() == null ? null : spec.validate().apply(typed);
                        if (error != null) {
                            KitPrompts.invalid(viewer, typed);
                            return;
                        }
                        List<String> updated = new ArrayList<>(spec.values().get());
                        updated.add(typed);
                        spec.save().accept(updated);
                    }, reopen);
                }));
        Card help = Card.of(KitStyle.HEX).tag(spec.title()).blank();
        spec.help().forEach(help::line);
        help.blank()
                .line(Tr.t("Clic gauche sur une ligne : modifier"))
                .line(Tr.t("Clic droit : remonter"))
                .line(Tr.t("Shift clic droit : supprimer"));
        gui.setItem(ROWS, 5, Guis.display(Material.BOOK, Palette.heading(spec.title()), help.build()));
        gui.setItem(ROWS, 7, new GuiItem(KitStyle.decorate(new ItemStack(Material.LAVA_BUCKET),
                Palette.ERROR + Tr.t("<b>Tout effacer</b>"), Card.of(Palette.ERROR_HEX).blank()
                        .count(Card.AMOUNT, Tr.t("Lignes"), values.size()).blank()
                        .click(Tr.t("Shift clic"), Tr.t("pour tout effacer")).build(), false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            if (!event.isShiftClick()) {
                Guis.deny(viewer);
                return;
            }
            Guis.click(viewer);
            spec.save().accept(List.of());
            reopen.run();
        }));
        if (back != null) {
            gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        }
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private static GuiItem entry(Spec spec, List<String> values, int index, Runnable reopen) {
        String value = values.get(index);
        List<String> lore = Card.of(KitStyle.HEX).tag(Tr.t("Ligne ") + (index + 1)).blank()
                .line(Mini.escape(value))
                .blank()
                .click(Tr.t("Clic gauche"), Tr.t("modifier"))
                .click(Tr.t("Clic droit"), Tr.t("remonter"))
                .click(Tr.t("Shift clic droit"), Tr.t("supprimer"))
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(spec.icon()), Palette.TEXT + "#" + (index + 1) + " "
                + Palette.MUTED + Mini.escape(shorten(value)), lore, false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            ClickType click = event.getClick();
            List<String> updated = new ArrayList<>(spec.values().get());
            if (index >= updated.size()) {
                reopen.run();
                return;
            }
            if (click == ClickType.SHIFT_RIGHT) {
                Guis.click(viewer);
                updated.remove(index);
                spec.save().accept(updated);
                reopen.run();
            } else if (click.isRightClick()) {
                if (index > 0) {
                    Guis.click(viewer);
                    String moved = updated.remove(index);
                    updated.add(index - 1, moved);
                    spec.save().accept(updated);
                }
                reopen.run();
            } else {
                Guis.click(viewer);
                KitPrompts.text(viewer, spec.prompt(), spec.longInput(), typed -> {
                    String error = spec.validate() == null ? null : spec.validate().apply(typed);
                    if (error != null) {
                        KitPrompts.invalid(viewer, typed);
                        return;
                    }
                    List<String> edited = new ArrayList<>(spec.values().get());
                    if (index < edited.size()) {
                        edited.set(index, typed);
                        spec.save().accept(edited);
                    }
                }, reopen);
            }
        });
    }

    static String shorten(String value) {
        return value.length() <= 32 ? value : value.substring(0, 31) + "…";
    }
}
