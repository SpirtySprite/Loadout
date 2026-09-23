package com.kirugoldzzzz.loadout;

import com.foliagui.gui.Gui;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.gui.DeferredPage;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.gui.ChatPrompts;
import com.kirugoldzzzz.loadout.common.item.Heads;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.Wallet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KitAdminMenu {

    static final int ROWS = 6;

    private final KitService service;
    private final KitEditor editor;
    private final KitEditorMenu kitEditor;
    private final KitPreviewMenu preview;
    private final Wallet economy;
    private KitProgressionMenu progression;

    public void bind(KitProgressionMenu progressionMenu) {
        this.progression = progressionMenu;
    }

    public KitAdminMenu(KitService service, KitEditor editor, KitEditorMenu kitEditor, KitPreviewMenu preview,
                        Wallet economy) {
        this.service = service;
        this.editor = editor;
        this.kitEditor = kitEditor;
        this.preview = preview;
        this.economy = economy;
    }

    public void edit(Player player, String id, Runnable back) {
        kitEditor.open(player, id, back);
    }

    public int capture(Player player, String id) {
        return kitEditor.capture(player, id);
    }

    public void open(Player player) {
        long started = System.nanoTime();
        PaginatedGui gui = PaginatedGui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.smallTitle("Administration des kits")))
                .create();
        Guis.paginationBar(gui, null);
        Runnable reopen = () -> open(player);
        gui.setItem(ROWS, 2, KitEditorMenu.action(Material.NETHER_STAR, "Créer un kit", List.of(
                        Palette.TEXT + "L'objet en main devient l'icône.", Palette.TEXT + "Vous choisissez l'identifiant."),
                "pour créer", viewer -> ChatPrompts.open(viewer, "l'identifiant", typed -> {
                    String slug = KitEditor.slug(typed == null ? "" : typed);
                    if (!KitLoader.ID.matcher(slug).matches()) {
                        Guis.deny(viewer);
                        Messages.send(viewer, "kits.invalid-id");
                        reopen.run();
                        return;
                    }
                    String id = editor.create(slug, viewer.getInventory().getItemInMainHand());
                    Messages.send(viewer, "kits.created", Mini.value("id", id));
                    kitEditor.open(viewer, id, reopen);
                })));
        gui.setItem(ROWS, 4, KitEditorMenu.action(Material.BOOKSHELF, "Catégories", List.of(Palette.TEXT
                        + service.catalog().categories().size() + " catégorie(s)"), "pour gérer",
                viewer -> categories(viewer, reopen)));
        gui.setItem(ROWS, 6, KitEditorMenu.action(Material.COMPARATOR, "Réglages généraux", List.of(
                        Palette.TEXT + "Rappels, animations, annonces,", Palette.TEXT + "bons et fuseau horaire."),
                "pour ouvrir", viewer -> settings(viewer, reopen)));
        gui.setItem(ROWS, 8, reloadButton(reopen));
        gui.setItem(ROWS, 1, KitEditorMenu.action(Material.EXPERIENCE_BOTTLE, "Progression", List.of(
                        Palette.TEXT + "Maîtrise, séries, collection", Palette.TEXT + "et kit du jour."),
                "pour configurer", viewer -> {
                    if (progression != null) {
                        progression.open(viewer, reopen);
                    }
                }));

        List<Kit> kits = new ArrayList<>(service.catalog().kits().values());
        if (kits.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB, Palette.MUTED + "<b>Aucun kit</b>",
                    Card.of(KitStyle.HEX).blank().line("Créez votre premier kit avec").line("le bouton en bas.").build()));
        }
        DeferredPage<Kit> page = Guis.deferred(gui, kits, 45, kit -> entry(kit, reopen));
        Guis.controls(gui, page);
        gui.open(player);
        Guis.opened(started);
    }

    private GuiItem entry(Kit kit, Runnable reopen) {
        KitRepository.Stats stats = service.repository().stats(kit.id());
        Card card = Card.of(KitStyle.HEX).tag("Kit " + kit.id()).blank()
                .stat(Card.CATEGORY, "Catégorie", kit.category() == null ? "aucune" : kit.category())
                .stat(Card.FLAG, "Permission", kit.permission() == null ? "aucune" : kit.permission())
                .count(Card.AMOUNT, "Objets", kit.contents().size())
                .stat(Card.TIME, "Recharge", kit.cooldown() <= 0L ? "aucune" : Numbers.duration(kit.cooldown()))
                .stat(Card.TIME, "Remise à zéro", KitStyle.resetLabel(kit))
                .blank()
                .count(Card.AMOUNT, "Récupérations", stats.claims())
                .count(Card.PLAYER, "Joueurs", stats.players());
        if (kit.empty()) {
            card.blank().deny("Kit vide, rien à donner");
        }
        card.blank()
                .click("Clic gauche", "éditer")
                .click("Clic droit", "aperçu joueur")
                .click("Shift clic droit", "se donner le kit");
        return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), kit.name(), card.build(), !kit.empty()), event -> {
            Player viewer = (Player) event.getWhoClicked();
            ClickType click = event.getClick();
            Guis.click(viewer);
            if (click == ClickType.SHIFT_RIGHT) {
                viewer.closeInventory();
                KitService.Outcome outcome = service.claim(viewer, kit, KitService.Source.ADMIN);
                if (!outcome.success()) {
                    Messages.send(viewer, "kits.give-failed", KitService.kitResolver(kit),
                            Mini.value("player", viewer.getName()));
                }
            } else if (click.isRightClick()) {
                preview.open(viewer, kit, reopen);
            } else {
                kitEditor.open(viewer, kit.id(), reopen);
            }
        });
    }

    private GuiItem reloadButton(Runnable reopen) {
        List<String> problems = service.catalog().problems();
        Card card = Card.of(KitStyle.HEX).tag("Fichier").blank()
                .line("Relit kits.yml après une")
                .line("modification à la main.")
                .blank()
                .count(Card.AMOUNT, "Kits chargés", service.catalog().kits().size());
        if (!problems.isEmpty()) {
            card.blank().deny(problems.size() + " problème(s) au dernier chargement");
            for (int index = 0; index < Math.min(4, problems.size()); index++) {
                card.raw(Palette.MUTED + KitListMenu.shorten(problems.get(index)));
            }
        }
        card.blank().click("pour recharger");
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.RECOVERY_COMPASS), Palette.heading("Recharger"),
                card.build(), !problems.isEmpty()), event -> {
            Player viewer = (Player) event.getWhoClicked();
            editor.reload();
            Guis.success(viewer);
            Messages.send(viewer, "kits.reloaded", Mini.value("amount",
                    String.valueOf(service.catalog().kits().size())));
            reopen.run();
        });
    }

    private void categories(Player player, Runnable back) {
        Runnable reopen = () -> categories(player, back);
        Gui gui = Gui.builder().rows(ROWS).title(Mini.parse(Palette.smallTitle("Catégories de kits"))).create();
        Guis.fill(gui);
        List<KitCategory> categories = new ArrayList<>(service.catalog().categories().values());
        for (int index = 0; index < Math.min(45, categories.size()); index++) {
            KitCategory category = categories.get(index);
            Card card = Card.of(KitStyle.HEX).tag("Catégorie " + category.id()).blank();
            category.description().forEach(card::line);
            card.blank()
                    .count(Card.AMOUNT, "Kits", service.catalog().inCategory(category.id()).size())
                    .stat(Card.SORT, "Ordre", category.order())
                    .blank()
                    .click("Clic gauche", "renommer")
                    .click("Clic droit", "description")
                    .click("Shift clic gauche", "icône depuis la main")
                    .click("Shift clic droit", "supprimer");
            gui.setItem(index, new GuiItem(KitStyle.decorate(new ItemStack(category.icon()), category.name(),
                    card.build(), false), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                switch (event.getClick()) {
                    case SHIFT_RIGHT -> {
                        int moved = editor.deleteCategory(category.id());
                        Messages.send(viewer, "kits.saved", Mini.value("id", category.id() + " (" + moved
                                + " kit(s) sans catégorie)"));
                        reopen.run();
                    }
                    case SHIFT_LEFT -> {
                        ItemStack held = viewer.getInventory().getItemInMainHand();
                        if (held.getType().isAir()) {
                            Guis.deny(viewer);
                            Messages.send(viewer, "kits.hold-item");
                        } else {
                            editor.setCategoryIcon(category.id(), held.getType());
                        }
                        reopen.run();
                    }
                    case RIGHT -> KitListMenu.open(viewer, new KitListMenu.Spec("Description", List.of(
                            "Texte affiché dans l'onglet."), "Ligne de texte", true,
                            () -> service.catalog().category(category.id()).map(KitCategory::description)
                                    .orElse(List.of()), lines -> editor.setCategoryDescription(category.id(), lines),
                            null, Material.PAPER), reopen);
                    default -> KitPrompts.text(viewer, "Nom affiché", true,
                            typed -> editor.setCategoryName(category.id(), typed), reopen);
                }
            }));
        }
        gui.setItem(ROWS, 3, KitEditorMenu.action(Material.LIME_DYE, "Nouvelle catégorie", List.of(
                        Palette.TEXT + "L'objet en main devient l'icône."), "pour créer",
                viewer -> ChatPrompts.open(viewer, "l'identifiant", typed -> {
                    String slug = KitEditor.slug(typed == null ? "" : typed);
                    if (!KitLoader.ID.matcher(slug).matches()) {
                        Guis.deny(viewer);
                        Messages.send(viewer, "kits.invalid-id");
                        reopen.run();
                        return;
                    }
                    ItemStack held = viewer.getInventory().getItemInMainHand();
                    editor.createCategory(slug, held.getType().isAir() ? Material.CHEST : held.getType());
                    reopen.run();
                })));
        gui.setItem(ROWS, 7, KitEditorMenu.action(Material.COMPARATOR, "Réordonner", List.of(
                        Palette.TEXT + "Écrivez l'identifiant puis la position,", Palette.TEXT + "ex debut 1."),
                "pour réordonner", viewer -> ChatPrompts.open(viewer, "l'identifiant et la position", typed -> {
                    String[] parts = typed == null ? new String[0] : typed.trim().split("\\s+");
                    int order = parts.length == 2 ? Numbers.parseInt(parts[1], Integer.MIN_VALUE) : Integer.MIN_VALUE;
                    if (order == Integer.MIN_VALUE || service.catalog().category(parts[0]).isEmpty()) {
                        KitPrompts.invalid(viewer, typed == null ? "" : typed);
                    } else {
                        editor.setCategoryOrder(parts[0], order);
                    }
                    reopen.run();
                })));
        gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void settings(Player player, Runnable back) {
        Runnable reopen = () -> settings(player, back);
        KitSettings settings = service.settings();
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle("Réglages des kits"))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, toggle("Kits verrouillés visibles", "Affiche les kits réservés avec leur indice",
                settings.showLocked(), "show-locked", reopen));
        gui.setItem(2, 3, toggle("Rappels", "Prévient quand un kit redevient disponible", settings.reminders(),
                "reminders", reopen));
        gui.setItem(2, 4, toggle("Résumé à la connexion", "Annonce le nombre de kits disponibles",
                settings.joinSummary(), "join-summary", reopen));
        gui.setItem(2, 5, toggle("Animations", "Coffre holographique à la récupération", settings.animation(),
                "animation", reopen));
        gui.setItem(2, 6, toggle("Annonces", "Autorise les kits à s'annoncer au serveur", settings.broadcasts(),
                "broadcasts", reopen));
        gui.setItem(2, 8, KitEditorMenu.action(Material.CLOCK, "Fuseau horaire", List.of(Palette.TEXT
                        + settings.zone().getId(), Palette.MUTED + "Utilisé pour les remises à zéro."), "pour changer",
                viewer -> KitPrompts.text(viewer, "ex Europe/Paris", true, typed -> {
                    try {
                        java.time.ZoneId.of(typed.trim());
                        editor.setSetting("timezone", typed.trim());
                    } catch (java.time.DateTimeException invalid) {
                        KitPrompts.invalid(viewer, typed);
                    }
                }, reopen)));
        gui.setItem(3, 4, KitEditorMenu.action(settings.voucherMaterial(), "Objet des bons", List.of(Palette.TEXT
                        + settings.voucherMaterial().name()), "pour utiliser l'objet en main",
                viewer -> {
                    ItemStack held = viewer.getInventory().getItemInMainHand();
                    if (held.getType().isAir()) {
                        Guis.deny(viewer);
                        Messages.send(viewer, "kits.hold-item");
                    } else {
                        editor.setSetting("voucher.material", held.getType().name());
                    }
                    reopen.run();
                }));
        gui.setItem(3, 6, KitEditorMenu.action(Material.LEAD, "Mention invendable", List.of(Palette.TEXT
                        + KitListMenu.shorten(Mini.plain(Mini.label(settings.protectedLore())))),
                "pour modifier", viewer -> KitPrompts.text(viewer, "Ligne ajoutée", true,
                        typed -> editor.setSetting("protected-lore", typed), reopen)));
        gui.setItem(4, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(4, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private GuiItem toggle(String title, String description, boolean enabled, String key, Runnable reopen) {
        List<String> lore = Card.of(KitStyle.HEX).tag("Réglage").blank()
                .line(description)
                .blank()
                .option(enabled, "Activé")
                .option(!enabled, "Désactivé")
                .blank()
                .click(enabled ? "pour désactiver" : "pour activer")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE),
                (enabled ? Palette.SUCCESS : Palette.MUTED) + "<b>" + title + "</b>", lore, enabled), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            editor.setSetting(key, !enabled);
            reopen.run();
        });
    }

    public void player(Player admin, UUID target, Runnable back) {
        Runnable reopen = () -> player(admin, target, back);
        String name = economy.nameOf(target);
        Player online = Bukkit.getPlayer(target);
        PaginatedGui gui = PaginatedGui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.smallTitle("Kits de " + name)))
                .create();
        Guis.paginationBar(gui, back);
        Map<String, KitClaim> claims = service.repository().claimsOf(target);
        long total = claims.values().stream().mapToLong(KitClaim::uses).sum();
        gui.setItem(ROWS, 5, new GuiItem(KitStyle.decorate(Heads.of(target), Palette.title(Mini.escape(name)),
                Card.of(KitStyle.HEX).tag("Joueur").blank()
                        .count(Card.AMOUNT, "Récupérations", total)
                        .count(Card.CATEGORY, "Kits différents", claims.size())
                        .stat(Card.PLAYER, "Connecté", online == null ? "non" : "oui")
                        .blank()
                        .click("Shift clic", "pour tout remettre à zéro")
                        .build(), false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            if (event.isShiftClick()) {
                int removed = service.reset(target, null);
                Guis.success(viewer);
                Messages.send(viewer, "kits.reset", Mini.value("count", String.valueOf(removed)),
                        Mini.value("player", name));
                reopen.run();
            }
        }));
        List<Kit> kits = new ArrayList<>(service.catalog().kits().values());
        long now = System.currentTimeMillis();
        KitViewer viewer = online == null ? null : service.viewer(online);
        DeferredPage<Kit> page = Guis.deferred(gui, kits, 45, kit -> {
            KitClaim claim = claims.get(kit.id());
            Card card = Card.of(KitStyle.HEX).tag("Kit " + kit.id()).blank();
            if (viewer != null) {
                card.raw(KitStyle.stateLine(service.status(viewer, kit, now), now));
            }
            card.count(Card.AMOUNT, "Récupérations", claim == null ? 0 : claim.uses())
                    .stat(Card.TIME, "Dernière", claim == null || claim.last() <= 0L ? "jamais"
                            : "il y a " + Numbers.duration(now - claim.last()))
                    .blank()
                    .click("Clic gauche", "effacer ses récupérations")
                    .click("Clic droit", "rendre une récupération")
                    .click("Shift clic gauche", "lui donner le kit")
                    .click("Shift clic droit", "lui donner un bon");
            return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), kit.name(), card.build(), claim != null), event -> {
                Player clicker = (Player) event.getWhoClicked();
                Guis.click(clicker);
                Player current = Bukkit.getPlayer(target);
                switch (event.getClick()) {
                    case SHIFT_LEFT -> {
                        if (current == null) {
                            Messages.send(clicker, "kits.offline");
                            return;
                        }
                        Scheduling.entity(current, () -> {
                            KitService.Outcome outcome = service.claim(current, kit, KitService.Source.ADMIN);
                            Messages.send(clicker, outcome.success() ? "kits.given" : "kits.give-failed",
                                    KitService.kitResolver(kit), Mini.value("player", current.getName()));
                        });
                    }
                    case SHIFT_RIGHT -> {
                        if (current == null) {
                            Messages.send(clicker, "kits.offline");
                            return;
                        }
                        service.giveVouchers(current, kit, 1, clicker.getName());
                        Messages.send(clicker, "kits.vouchers-given", KitService.kitResolver(kit),
                                Mini.value("amount", "1"), Mini.value("player", current.getName()));
                    }
                    case RIGHT -> {
                        service.repository().undo(target, kit.id());
                        Messages.send(clicker, "kits.undone", KitService.kitResolver(kit), Mini.value("player", name));
                        reopen.run();
                    }
                    default -> {
                        int removed = service.reset(target, kit.id());
                        Messages.send(clicker, "kits.reset", Mini.value("count", String.valueOf(removed)),
                                Mini.value("player", name));
                        reopen.run();
                    }
                }
            });
        });
        Guis.controls(gui, page);
        gui.open(admin);
    }
}
