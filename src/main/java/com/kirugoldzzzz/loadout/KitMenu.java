package com.kirugoldzzzz.loadout;

import com.foliagui.animation.GuiAnimation;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.item.Heads;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class KitMenu {

    static final int ROWS = 6;
    static final int FIRST_ROW = 2;
    static final int GRID_ROWS = 4;
    static final int FIRST_COLUMN = 2;
    static final int COLUMNS = 7;
    static final int PAGE_SIZE = GRID_ROWS * COLUMNS;
    static final int TAB_LIMIT = 9;
    static final long REFRESH_TICKS = 20L;

    public enum Filter {
        ALL("Tous les kits", Material.HOPPER),
        FAVORITES("Favoris", Material.NETHER_STAR),
        AVAILABLE("Disponibles", Material.LIME_DYE),
        WAITING("En recharge", Material.CLOCK),
        LOCKED("Verrouillés", Material.GRAY_DYE);

        private final String label;
        private final Material icon;

        Filter(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }

        Filter next() {
            Filter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        boolean accepts(KitStatus status, boolean favorite) {
            return switch (this) {
                case ALL -> true;
                case FAVORITES -> favorite;
                case AVAILABLE -> status.available();
                case WAITING -> status.state() == KitStatus.State.COOLDOWN;
                case LOCKED -> !status.available() && status.state() != KitStatus.State.COOLDOWN;
            };
        }
    }

    private static final class View {
        String category = KitCategory.ALL;
        Filter filter = Filter.ALL;
        int page;
    }

    private final KitActions actions;
    private final KitPreviewMenu preview;
    private final PlayerSettingsRepository settings;
    private KitCollectionMenu collection;
    private KitHistoryMenu history;

    public KitMenu(KitActions actions, KitPreviewMenu preview, PlayerSettingsRepository settings) {
        this.actions = actions;
        this.preview = preview;
        this.settings = settings;
    }

    public void bind(KitCollectionMenu collectionMenu, KitHistoryMenu historyMenu) {
        this.collection = collectionMenu;
        this.history = historyMenu;
    }

    public void open(Player player) {
        open(player, KitCategory.ALL);
    }

    public void open(Player player, String category) {
        View view = new View();
        view.category = category == null ? KitCategory.ALL : category.toLowerCase(Locale.ROOT);
        open(player, view);
    }

    private void open(Player player, View view) {
        long started = System.nanoTime();
        Gui gui = Gui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.smallTitle("Kits")))
                .create();
        Guis.fill(gui);
        render(gui, player, view);
        gui.open(player);
        GuiAnimation.play(gui, player, REFRESH_TICKS, animated -> render(gui, player, view));
        Guis.opened(started);
    }

    static int[] tabColumns(int count) {
        int shown = Math.max(1, Math.min(TAB_LIMIT, count));
        int[] columns = new int[shown];
        int start = 5 - (shown - 1) / 2;
        if (shown % 2 == 0) {
            start = Math.max(1, 5 - shown / 2);
        }
        for (int index = 0; index < shown; index++) {
            columns[index] = start + index;
        }
        return columns;
    }

    private void render(Gui gui, Player player, View view) {
        KitService service = actions.service();
        KitCatalog catalog = service.catalog();
        KitViewer viewer = service.viewer(player);
        long now = System.currentTimeMillis();

        List<KitCategory> categories = new ArrayList<>(catalog.categories().values());
        int tabCount = Math.min(TAB_LIMIT, categories.size() + 1);
        int[] tabs = tabColumns(tabCount);
        for (int column = 1; column <= 9; column++) {
            gui.updateItem(Guis.slot(1, column), Guis.filler());
        }
        gui.updateItem(Guis.slot(1, tabs[0]), allTab(player, view, catalog));
        for (int index = 1; index < tabCount; index++) {
            gui.updateItem(Guis.slot(1, tabs[index]), tab(player, view, categories.get(index - 1)));
        }

        List<Kit> shown = new ArrayList<>();
        List<KitStatus> statuses = new ArrayList<>();
        List<Kit> favoriteKits = new ArrayList<>();
        List<KitStatus> favoriteStatuses = new ArrayList<>();
        Set<String> favorites = service.repository().favorites(player.getUniqueId());
        int available = 0;
        int freeAvailable = 0;
        long soonest = Long.MAX_VALUE;
        for (Kit kit : catalog.kits().values()) {
            KitStatus status = service.status(viewer, kit, now);
            if (status.available()) {
                available++;
                if (service.cost(kit, now).free() && !kit.options().roulette()) {
                    freeAvailable++;
                }
            } else if (status.state() == KitStatus.State.COOLDOWN) {
                soonest = Math.min(soonest, status.remaining());
            }
            boolean favorite = favorites.contains(kit.id());
            boolean inCategory = view.category.equals(KitCategory.ALL) || view.category.equals(kit.category());
            if (inCategory && service.visible(status, kit) && view.filter.accepts(status, favorite)) {
                if (favorite) {
                    favoriteKits.add(kit);
                    favoriteStatuses.add(status);
                } else {
                    shown.add(kit);
                    statuses.add(status);
                }
            }
        }
        shown.addAll(0, favoriteKits);
        statuses.addAll(0, favoriteStatuses);
        int pages = Math.max(1, (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        view.page = Math.max(0, Math.min(view.page, pages - 1));
        int offset = view.page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE; index++) {
            int slot = Guis.slot(FIRST_ROW + index / COLUMNS, FIRST_COLUMN + index % COLUMNS);
            int position = offset + index;
            if (position < shown.size()) {
                gui.updateItem(slot, kitItem(gui, player, viewer, view, shown.get(position), statuses.get(position)));
            } else if (shown.isEmpty() && index == PAGE_SIZE / 2 - COLUMNS / 2 - 1) {
                gui.updateItem(slot, empty(view));
            } else {
                gui.updateItem(slot, Guis.filler());
            }
        }

        gui.updateItem(Guis.slot(ROWS, 1), historyButton(view));
        gui.updateItem(Guis.slot(ROWS, 2), filterButton(gui, player, view));
        gui.updateItem(Guis.slot(ROWS, 3), view.page > 0 ? pageButton(gui, player, view, -1, pages) : Guis.filler());
        gui.updateItem(Guis.slot(ROWS, 4), claimAllButton(gui, view, freeAvailable));
        gui.updateItem(Guis.slot(ROWS, 5), summary(player, available, catalog.kits().size(), soonest, now));
        gui.updateItem(Guis.slot(ROWS, 6), collectionButton(player, view));
        gui.updateItem(Guis.slot(ROWS, 7), view.page < pages - 1 ? pageButton(gui, player, view, 1, pages)
                : Guis.filler());
        gui.updateItem(Guis.slot(ROWS, 8), reminderButton(gui, player, view));
        gui.updateItem(Guis.slot(ROWS, 9), Guis.closeButton());
    }

    private GuiItem allTab(Player player, View view, KitCatalog catalog) {
        boolean selected = view.category.equals(KitCategory.ALL);
        List<String> lore = Card.of(KitStyle.HEX).tag("Catégorie")
                .blank()
                .line("Tous les kits du serveur.")
                .blank()
                .count(Card.AMOUNT, "Kits", catalog.kits().size())
                .blank()
                .click(selected ? "catégorie affichée" : "pour afficher")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.NETHER_STAR),
                (selected ? Palette.SECONDARY : Palette.TEXT) + "<b>Tous</b>", lore, selected), event -> {
            Player viewer = (Player) event.getWhoClicked();
            select(viewer, view, KitCategory.ALL);
        });
    }

    private GuiItem tab(Player player, View view, KitCategory category) {
        boolean selected = view.category.equals(category.id());
        Card card = Card.of(KitStyle.HEX).tag("Catégorie").blank();
        category.description().forEach(card::line);
        card.blank().count(Card.AMOUNT, "Kits", actions.service().catalog().inCategory(category.id()).size())
                .blank()
                .click(selected ? "catégorie affichée" : "pour afficher");
        return new GuiItem(KitStyle.decorate(new ItemStack(category.icon()), category.name(), card.build(), selected),
                event -> select((Player) event.getWhoClicked(), view, category.id()));
    }

    private void select(Player viewer, View view, String category) {
        if (view.category.equals(category)) {
            return;
        }
        Guis.click(viewer);
        view.category = category;
        view.page = 0;
        open(viewer, view);
    }

    private GuiItem kitItem(Gui gui, Player player, KitViewer kitViewer, View view, Kit kit, KitStatus status) {
        return new GuiItem(actions.icon(player, kitViewer, kit, status, true), event -> {
            Player viewer = (Player) event.getWhoClicked();
            ClickType click = event.getClick();
            if (click == ClickType.SHIFT_RIGHT) {
                boolean added = actions.service().toggleFavorite(viewer, kit);
                if (added) {
                    Guis.success(viewer);
                } else {
                    Guis.click(viewer);
                }
                render(gui, viewer, view);
                return;
            }
            if (click.isShiftClick()) {
                Guis.click(viewer);
                actions.promptGift(viewer, kit, () -> open(viewer, view));
                return;
            }
            if (click.isRightClick()) {
                Guis.click(viewer);
                preview.open(viewer, kit, () -> open(viewer, view));
                return;
            }
            actions.request(viewer, kit, KitService.Source.MENU, () -> {
                if (gui.isOpenFor(viewer)) {
                    render(gui, viewer, view);
                } else {
                    open(viewer, view);
                }
            });
        });
    }

    private GuiItem empty(View view) {
        return Guis.display(Material.COBWEB, Palette.MUTED + "<b>" + Card.small("Aucun kit") + "</b>",
                Card.of(KitStyle.HEX).blank()
                        .line(view.filter == Filter.ALL ? "Rien dans cette catégorie." : "Aucun kit ne correspond")
                        .line(view.filter == Filter.ALL ? "" : "au filtre " + view.filter.label.toLowerCase(Locale.ROOT) + ".")
                        .build());
    }

    private GuiItem filterButton(Gui gui, Player player, View view) {
        Card card = Card.of(KitStyle.HEX).tag("Filtre").blank();
        for (Filter filter : Filter.values()) {
            card.option(filter == view.filter, filter.label);
        }
        card.blank().click("pour changer de filtre");
        return new GuiItem(KitStyle.decorate(new ItemStack(view.filter.icon),
                Palette.heading("Filtre : " + view.filter.label), card.build(), false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            view.filter = view.filter.next();
            view.page = 0;
            render(gui, viewer, view);
        });
    }

    private GuiItem pageButton(Gui gui, Player player, View view, int step, int pages) {
        String label = step < 0 ? Palette.BACK + " Page précédente" : "Page suivante " + Palette.POINTER;
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.ARROW), Palette.ACCENT + label,
                Card.of(KitStyle.HEX).tag("Navigation").stat(Card.FLAG, "Page", (view.page + 1) + " / " + pages)
                        .build(), false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            view.page += step;
            render(gui, viewer, view);
        });
    }

    private GuiItem summary(Player player, int available, int total, long soonest, long now) {
        KitService service = actions.service();
        Card card = Card.of(KitStyle.HEX).tag("Vos kits").blank()
                .stat(Palette.SUCCESS, Palette.CHECK, "Disponibles", available + " / " + total)
                .stat(Card.CATEGORY, "Collection", service.collected(player.getUniqueId()).size() + " / " + total);
        if (soonest != Long.MAX_VALUE) {
            card.stat(Palette.WARNING, Card.TIME, "Prochain prêt dans", Numbers.duration(soonest));
        }
        String featured = service.featured(now);
        if (featured != null) {
            service.kit(featured).ifPresent(kit -> card.section("Kit du jour")
                    .raw(Palette.WARNING + "⚡ " + kit.name())
                    .stat(Palette.SUCCESS, "✚", "Réduction", "-" + service.catalog().progression().featured().discount()
                            + "%")
                    .stat(Card.TIME, "Change dans", Numbers.duration(service.catalog().progression().featured()
                            .nextRotation(now, service.settings().zone()) - now)));
        }
        card.blank()
                .line("Clic gauche sur un kit pour le")
                .line("récupérer, clic droit pour voir")
                .line("tout son contenu, shift clic droit")
                .line("pour l'épingler en favori.");
        ItemStack head = Heads.of(player.getUniqueId());
        return new GuiItem(KitStyle.decorate(head, Palette.title(Mini.escape(player.getName())), card.build(),
                false), event -> event.setCancelled(true));
    }

    private GuiItem historyButton(View view) {
        List<String> lore = Card.of(KitStyle.HEX).tag("Historique").blank()
                .line("Toutes vos récupérations, cadeaux")
                .line("et bons utilisés.")
                .blank()
                .click("pour ouvrir")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.WRITABLE_BOOK), Palette.heading("Historique"),
                lore, false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            if (history == null) {
                return;
            }
            Guis.click(viewer);
            history.open(viewer, 0, () -> open(viewer, view));
        });
    }

    private GuiItem collectionButton(Player player, View view) {
        KitService service = actions.service();
        int owned = service.collected(player.getUniqueId()).size();
        int total = service.catalog().kits().size();
        List<String> lore = Card.of(KitStyle.HEX).tag("Collection").blank()
                .line("Découvrez tous les kits et")
                .line("débloquez des récompenses.")
                .blank()
                .stat(Card.CATEGORY, "Découverts", owned + " / " + total)
                .raw(KitStyle.progress(total == 0 ? 0.0D : owned / (double) total))
                .blank()
                .click("pour ouvrir")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.KNOWLEDGE_BOOK), Palette.heading("Collection"),
                lore, owned == total && total > 0), event -> {
            Player viewer = (Player) event.getWhoClicked();
            if (collection == null) {
                return;
            }
            Guis.click(viewer);
            collection.open(viewer, () -> open(viewer, view));
        });
    }

    private GuiItem claimAllButton(Gui gui, View view, int ready) {
        List<String> lore = Card.of(KitStyle.HEX).tag("Tout récupérer").blank()
                .line("Récupère d'un coup tous les kits")
                .line("gratuits disponibles.")
                .blank()
                .count(Card.AMOUNT, "Kits prêts", ready)
                .blank()
                .raw(ready > 0 ? Card.clickLine("pour tout récupérer") : Card.denyLine("Rien à récupérer"))
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(ready > 0 ? Material.HOPPER_MINECART : Material.MINECART),
                (ready > 0 ? Palette.SUCCESS : Palette.MUTED) + "<b>Tout récupérer</b>", lore, ready > 0), event -> {
            Player viewer = (Player) event.getWhoClicked();
            if (ready <= 0) {
                Guis.deny(viewer);
                return;
            }
            actions.service().claimAll(viewer);
            render(gui, viewer, view);
        });
    }

    private GuiItem reminderButton(Gui gui, Player player, View view) {
        boolean enabled = settings.get(player.getUniqueId()).enabled(PlayerSettings.Setting.KIT_REMINDERS);
        List<String> lore = Card.of(KitStyle.HEX).tag("Rappels").blank()
                .line("Un message vous prévient dès")
                .line("qu'un kit est de nouveau prêt.")
                .blank()
                .option(enabled, "Activés")
                .option(!enabled, "Désactivés")
                .blank()
                .click(enabled ? "pour désactiver" : "pour activer")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(enabled ? Material.BELL : Material.GRAY_DYE),
                Palette.heading("Rappels de kits"), lore, enabled), event -> {
            Player viewer = (Player) event.getWhoClicked();
            settings.toggle(viewer.getUniqueId(), PlayerSettings.Setting.KIT_REMINDERS);
            Guis.click(viewer);
            render(gui, viewer, view);
        });
    }
}
