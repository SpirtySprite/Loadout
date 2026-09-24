package com.kirugoldzzzz.loadout.common.gui;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.BaseGui;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.gui.SignGui;
import com.foliagui.item.GuiAction;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.diag.Diagnostics;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.common.text.Tr;
import com.kirugoldzzzz.loadout.common.util.LruCache;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Guis {

    public static final int BACK_SLOT = 1;
    public static final int PREVIOUS_SLOT = 3;
    public static final int INDICATOR_SLOT = 5;
    public static final int NEXT_SLOT = 7;
    public static final int CLOSE_SLOT = 9;

    private static final int SKIN_CAPACITY = 2048;
    private static final LruCache<Skin, ItemStack> SKINS = new LruCache<>(SKIN_CAPACITY);
    private static final LruCache<Skin, GuiItem> DISPLAYS = new LruCache<>(SKIN_CAPACITY);
    private static final GuiAction<InventoryClickEvent> CANCEL = event -> event.setCancelled(true);
    private static volatile Predicate<UUID> soundFilter = ignored -> true;

    private Guis() {
    }

    public static void invalidate() {
        SKINS.clear();
        DISPLAYS.clear();
    }

    public static int cachedSkins() {
        return SKINS.size();
    }

    static ItemStack skin(Material material, String name, List<String> lore, boolean glow) {
        Skin key = new Skin(material, name, lore == null ? List.of() : List.copyOf(lore), glow);
        return skin(key);
    }

    private static ItemStack skin(Skin key) {
        ItemStack cached = SKINS.get(key);
        if (cached != null) {
            return cached;
        }
        ItemStack built = ItemBuilder.of(key.material())
                .name(Mini.label(key.name()))
                .loreComponents(Mini.labels(key.lore()))
                .glow(key.glow())
                .build();
        SKINS.put(key, built);
        return built;
    }

    private record Skin(Material material, String name, List<String> lore, boolean glow) {
    }

    public static int slot(int row, int column) {
        return (row - 1) * 9 + (column - 1);
    }

    public static GuiItem filler() {
        return Chrome.FILLER;
    }

    public static GuiItem button(Material material, String name, List<String> lore, Consumer<Player> action) {
        return new GuiItem(skin(material, name, lore, false), event -> {
            Player player = (Player) event.getWhoClicked();
            click(player);
            action.accept(player);
        });
    }

    public static GuiItem display(Material material, String name, List<String> lore) {
        Skin key = new Skin(material, name, lore == null ? List.of() : List.copyOf(lore), false);
        GuiItem cached = DISPLAYS.get(key);
        if (cached != null) {
            return cached;
        }
        GuiItem display = new GuiItem(skin(key), CANCEL);
        DISPLAYS.put(key, display);
        return display;
    }

    public static GuiItem item(Material material, String name, List<String> lore,
                               GuiAction<InventoryClickEvent> action) {
        return new GuiItem(skin(material, name, lore, false), action);
    }

    public static GuiItem item(Material material, String name, List<String> lore, boolean glow,
                               GuiAction<InventoryClickEvent> action) {
        return new GuiItem(skin(material, name, lore, glow), action);
    }

    public static GuiItem glowing(Material material, String name, List<String> lore, boolean glow,
                                  Consumer<Player> action) {
        return new GuiItem(skin(material, name, lore, glow), event -> {
            Player player = (Player) event.getWhoClicked();
            click(player);
            action.accept(player);
        });
    }

    public static ItemStack described(ItemStack base, String name, List<String> extraLore) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        if (name != null) {
            meta.displayName(Mini.label(name));
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(Mini.labels(extraLore));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    public static void fill(BaseGui gui) {
        GuiItem filler = filler();
        for (int row = 1; row <= gui.getRows(); row++) {
            for (int column = 1; column <= 9; column++) {
                gui.setItem(row, column, filler);
            }
        }
    }

    public static void fillAnimated(BaseGui gui) {
        ItemStack pane = Chrome.FILLER.getItemStack();
        for (int slot = 0; slot < gui.getRows() * 9; slot++) {
            gui.setItem(slot, new GuiItem(pane, CANCEL));
        }
    }

    public static void opened(long startNanos) {
        Diagnostics.record(Diagnostics.MENU_OPEN, System.nanoTime() - startNanos);
    }

    public static void bottomBar(BaseGui gui) {
        GuiItem filler = filler();
        for (int column = 1; column <= 9; column++) {
            gui.setItem(gui.getRows(), column, filler);
        }
    }

    public static void paginationBar(PaginatedGui gui, Runnable back) {
        bottomBar(gui);
        if (back != null) {
            gui.setItem(gui.getRows(), BACK_SLOT, backButton(back));
        }
        gui.setItem(gui.getRows(), CLOSE_SLOT, closeButton());
        controls(gui);
    }

    public static void installTheme() {
        com.foliagui.FoliaGUI.theme(MenuTheme.create());
    }

    public static void controls(PaginatedGui gui) {
        int row = gui.getRows();
        gui.pageControls(slot(row, PREVIOUS_SLOT), slot(row, INDICATOR_SLOT), slot(row, NEXT_SLOT));
    }

    public static void controls(PaginatedGui gui, DeferredPage<?> page) {
        controls(gui);
    }

    public static void refresh(PaginatedGui gui) {
        controls(gui);
        gui.update();
    }

    public static <T> DeferredPage<T> deferred(PaginatedGui gui, List<T> entries, int pageSize,
                                                Function<T, GuiItem> renderer) {
        return new DeferredPage<>(gui, entries, pageSize, renderer);
    }

    public static void arrows(PaginatedGui gui, Runnable after) {
        int row = gui.getRows();
        gui.setItem(row, PREVIOUS_SLOT, gui.hasPrevious() ? pageButton(gui, true, after) : filler());
        gui.setItem(row, NEXT_SLOT, gui.hasNext() ? pageButton(gui, false, after) : filler());
    }

    public static void arrows(PaginatedGui gui, DeferredPage<?> page, Runnable after) {
        arrows(gui, () -> {
            arrows(gui, page, after);
            after.run();
        });
    }

    private static GuiItem pageButton(PaginatedGui gui, boolean previous, Runnable after) {
        return new GuiItem(skin(Material.ARROW, Card.title(Palette.PRIMARY_HEX, Palette.SECONDARY_HEX,
                        Card.small(previous ? Tr.t("Page précédente") : Tr.t("Page suivante"))),
                Card.of(Palette.PRIMARY_HEX).tag(Tr.t("Navigation"))
                        .stat(Card.FLAG, Tr.t("Page actuelle"), gui.getCurrentPage())
                        .stat(Card.AMOUNT, Tr.t("Pages au total"), gui.getPagesCount())
                        .blank()
                        .click(previous ? Tr.t("pour reculer d'une page") : Tr.t("pour avancer d'une page"))
                        .build(), false),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    if (previous ? gui.previous() : gui.next()) {
                        click(player);
                        after.run();
                    } else {
                        deny(player);
                    }
                });
    }

    public static GuiItem pageIndicator(PaginatedGui gui) {
        return new GuiItem(skin(Material.PAPER,
                Card.title(Palette.PRIMARY_HEX, Palette.SECONDARY_HEX,
                        Card.small(Tr.t("Page ") + gui.getCurrentPage() + Tr.t(" sur ") + gui.getPagesCount())),
                Card.of(Palette.PRIMARY_HEX).tag(Tr.t("Navigation"))
                        .count(Card.AMOUNT, Tr.t("Entrées affichées"), gui.getPageItemsCount())
                        .blank()
                        .click(Tr.t("pour choisir une page"))
                        .build(), false),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    click(player);
                    promptPage(gui, player);
                });
    }

    public static GuiItem backButton(Runnable back) {
        return new GuiItem(skin(Material.ARROW,
                Card.title(Palette.PRIMARY_HEX, Palette.SECONDARY_HEX, Card.small(Tr.t("Retour"))),
                Card.of(Palette.PRIMARY_HEX).tag(Tr.t("Navigation"))
                        .click(Tr.t("pour revenir au menu précédent")).build(), false),
                event -> {
                    click((Player) event.getWhoClicked());
                    back.run();
                });
    }

    public static GuiItem closeButton() {
        return Chrome.CLOSE;
    }

    public static void promptLongSign(Player player, String label, Consumer<String> onResult) {
        SignGui.builder()
                .line(3, label)
                .onComplete((viewer, lines) -> {
                    StringBuilder typed = new StringBuilder();
                    for (int index = 0; index < Math.min(3, lines.size()); index++) {
                        String line = lines.get(index);
                        if (line != null && !line.isBlank()) {
                            typed.append(typed.isEmpty() ? "" : " ").append(line.trim());
                        }
                    }
                    onResult.accept(typed.toString());
                })
                .open(player);
    }

    public static void promptSign(Player player, String label, Consumer<String> onResult) {
        SignGui.builder()
                .line(2, "^^^^^^^^^^^^^^^")
                .line(3, label)
                .onComplete((viewer, lines) -> {
                    String typed = lines.isEmpty() ? "" : lines.get(0);
                    onResult.accept(typed == null ? "" : typed.trim());
                })
                .open(player);
    }

    private static void promptPage(PaginatedGui gui, Player player) {
        promptSign(player, "Page 1 a " + gui.getPagesCount(), typed -> {
            int page = Numbers.parseInt(typed, -1);
            if (page < 1 || page > gui.getPagesCount()) {
                deny(player);
                gui.open(player);
                return;
            }
            gui.openPage(page);
            gui.open(player);
        });
    }

    public static void click(Player player) {
        if (!soundFilter.test(player.getUniqueId())) return;
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5F, 1.5F);
    }

    public static void success(Player player) {
        if (!soundFilter.test(player.getUniqueId())) return;
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.8F);
    }

    public static void money(Player player) {
        if (!soundFilter.test(player.getUniqueId())) return;
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, 1.4F);
    }

    public static void deny(Player player) {
        if (!soundFilter.test(player.getUniqueId())) return;
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.7F);
    }

    public static void soundFilter(Predicate<UUID> filter) {
        soundFilter = filter == null ? ignored -> true : filter;
    }

    private static final class Chrome {

        static final GuiItem FILLER = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
                .name(Mini.label(" "))
                .asGuiItem();

        static final GuiItem CLOSE = ItemBuilder.of(Material.BARRIER)
                .name(Mini.label(Card.title(Palette.ERROR_HEX, "#FCA5A5", Card.small(Tr.t("Fermer")))))
                .loreComponents(Mini.labels(Card.of(Palette.ERROR_HEX)
                        .tag(Tr.t("Navigation"))
                        .click(Tr.t("pour fermer ce menu"))
                        .build()))
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    click(player);
                    player.closeInventory();
                });

        private Chrome() {
        }
    }
}
