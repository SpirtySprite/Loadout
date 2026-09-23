package com.kirugoldzzzz.loadout;

import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.gui.ConfirmMenu;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.gui.ChatPrompts;
import com.kirugoldzzzz.loadout.common.item.ItemReturn;
import com.kirugoldzzzz.loadout.common.item.Shulkers;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class KitEditorMenu {

    static final int ROWS = 6;

    private final KitService service;
    private final KitEditor editor;
    private final KitActions actions;
    private final KitPreviewMenu preview;
    private final CrateBridge crates;
    private KitProgressionMenu progression;

    public KitEditorMenu(KitService service, KitEditor editor, KitActions actions, KitPreviewMenu preview,
                         CrateBridge crates) {
        this.service = service;
        this.editor = editor;
        this.actions = actions;
        this.preview = preview;
        this.crates = crates;
    }

    public void bind(KitProgressionMenu progressionMenu) {
        this.progression = progressionMenu;
    }

    private Optional<Kit> current(Player player, String id, Runnable back) {
        Optional<Kit> kit = service.kit(id);
        if (kit.isEmpty()) {
            Guis.deny(player);
            Messages.send(player, "kits.unknown", Mini.value("id", id));
            if (back != null) {
                back.run();
            } else {
                player.closeInventory();
            }
        }
        return kit;
    }

    public void open(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        long started = System.nanoTime();
        Runnable reopen = () -> open(player, id, back);
        Gui gui = Gui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.smallTitle("Éditer " + kit.id())))
                .create();
        Guis.fill(gui);

        gui.setItem(1, 5, header(kit));

        gui.setItem(2, 2, action(Material.NAME_TAG, "Nom", List.of(Palette.TEXT + "Actuel : " + kit.name()),
                "pour renommer", viewer -> KitPrompts.text(viewer, "Nouveau nom", true,
                        typed -> editor.setName(id, typed), reopen)));
        gui.setItem(2, 3, action(Material.WRITABLE_BOOK, "Description", preview(kit.description()),
                "pour modifier les lignes", viewer -> KitListMenu.open(viewer, new KitListMenu.Spec("Description",
                        List.of("Texte affiché sous le nom du kit."), "Ligne de texte", true, () -> service.kit(id)
                        .map(Kit::description).orElse(List.of()), lines -> editor.setDescription(id, lines), null,
                        Material.PAPER), reopen)));
        gui.setItem(2, 4, iconButton(kit, reopen));
        gui.setItem(2, 5, categoryButton(kit, reopen));
        gui.setItem(2, 6, action(Material.COMPARATOR, "Ordre", List.of(Palette.TEXT + "Position : " + kit.order()),
                "pour changer la position", viewer -> KitPrompts.integer(viewer, "Position", -1000, 1000,
                        value -> editor.setOrder(id, value), reopen)));
        gui.setItem(2, 7, permissionButton(kit, reopen));
        gui.setItem(2, 8, action(Material.MAP, "Indice de déblocage", preview(kit.hint()),
                "pour modifier", viewer -> KitListMenu.open(viewer, new KitListMenu.Spec("Indice de déblocage",
                        List.of("Affiché quand le kit est verrouillé,", "par exemple le grade à acheter."),
                        "Ligne d'indice", true, () -> service.kit(id).map(Kit::hint).orElse(List.of()),
                        lines -> editor.setHint(id, lines), null, Material.PAPER), reopen)));

        gui.setItem(3, 2, action(Material.CHEST, "Contenu", List.of(Palette.TEXT + kit.contents().size()
                        + " objet(s) placés"), "pour éditer comme un coffre",
                viewer -> new KitContentsEditor(editor, kit, reopen).open(viewer)));
        gui.setItem(3, 3, action(Material.ENDER_EYE, "Tirage aléatoire", List.of(Palette.TEXT
                        + kit.pool().effectiveRolls() + " tirage(s) parmi " + kit.pool().entries().size()),
                "pour configurer", viewer -> pool(viewer, id, reopen)));
        gui.setItem(3, 4, action(Material.GOLD_INGOT, "Récompenses", rewardSummary(kit), "pour configurer",
                viewer -> rewards(viewer, id, reopen)));
        gui.setItem(3, 5, action(Material.IRON_BARS, "Conditions", conditionSummary(kit), "pour configurer",
                viewer -> requirements(viewer, id, reopen)));
        gui.setItem(3, 6, action(Material.EMERALD, "Prix", costSummary(kit.cost()), "pour configurer",
                viewer -> cost(viewer, id, reopen)));
        gui.setItem(3, 7, action(Material.CLOCK, "Recharge et limites", limitSummary(kit), "pour configurer",
                viewer -> limits(viewer, id, reopen)));
        gui.setItem(3, 8, action(Material.REDSTONE_TORCH, "Options", optionSummary(kit), "pour configurer",
                viewer -> options(viewer, id, reopen)));

        gui.setItem(4, 2, action(Material.ARMOR_STAND, "Copier mon inventaire", List.of(
                        Palette.TEXT + "Le contenu devient une copie exacte", Palette.TEXT + "de votre inventaire."),
                "pour copier", viewer -> {
                    int captured = capture(viewer, id);
                    Messages.send(viewer, "kits.captured", Mini.value("id", id),
                            Mini.value("amount", String.valueOf(captured)));
                    reopen.run();
                }));
        gui.setItem(4, 3, action(Material.HOPPER, "Recevoir le contenu", List.of(
                        Palette.TEXT + "Donne une copie des objets du kit", Palette.TEXT + "pour les retoucher."),
                "pour recevoir", viewer -> {
                    ItemReturn.give(viewer, kit.contents().values(), "Contenu de kit");
                    Messages.send(viewer, "kits.loaded", Mini.value("id", id));
                    reopen.run();
                }));
        gui.setItem(4, 4, action(Material.PAPER, "Recevoir un bon", List.of(
                        Palette.TEXT + "Un bon physique de ce kit."), "pour recevoir",
                viewer -> {
                    service.giveVouchers(viewer, kit, 1, viewer.getName());
                    reopen.run();
                }));
        gui.setItem(4, 5, action(Material.SPYGLASS, "Aperçu joueur", List.of(
                        Palette.TEXT + "Le kit tel que les joueurs le voient."), "pour ouvrir",
                viewer -> preview.open(viewer, kit, reopen)));
        gui.setItem(4, 6, action(Material.TOTEM_OF_UNDYING, "Tester", List.of(
                        Palette.TEXT + "Vous donne le kit sans payer ni", Palette.TEXT + "compter de récupération."),
                "pour tester", viewer -> {
                    viewer.closeInventory();
                    actions.report(viewer, service.claim(viewer, kit, KitService.Source.ADMIN));
                }));
        gui.setItem(4, 7, statsButton(kit, reopen));
        gui.setItem(4, 8, action(Material.STRUCTURE_VOID, "Dupliquer", List.of(
                        Palette.TEXT + "Crée une copie modifiable."), "pour dupliquer",
                viewer -> {
                    String copy = editor.duplicate(id);
                    Messages.send(viewer, "kits.duplicated", Mini.value("id", copy));
                    open(viewer, copy, back);
                }));

        gui.setItem(5, 5, deleteButton(kit, back, reopen));
        if (back != null) {
            gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        }
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
        Guis.opened(started);
    }

    int capture(Player player, String id) {
        Map<Integer, ItemStack> contents = KitContentsEditor.snapshot(player.getInventory());
        editor.setContents(id, contents);
        return contents.size();
    }

    private GuiItem header(Kit kit) {
        List<String> lore = Card.of(KitStyle.HEX).tag("Kit " + kit.id()).blank()
                .stat(Card.CATEGORY, "Catégorie", kit.category() == null ? "aucune" : kit.category())
                .stat(Card.FLAG, "Permission", kit.permission() == null ? "aucune" : kit.permission())
                .count(Card.AMOUNT, "Objets", kit.contents().size())
                .build();
        return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), kit.name(), lore, true),
                event -> event.setCancelled(true));
    }

    private static List<String> preview(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of(Palette.MUTED + "Vide");
        }
        List<String> shown = new ArrayList<>();
        for (int index = 0; index < Math.min(4, lines.size()); index++) {
            shown.add(Palette.TEXT + lines.get(index));
        }
        if (lines.size() > 4) {
            shown.add(Palette.MUTED + "et " + (lines.size() - 4) + " autre(s)");
        }
        return shown;
    }

    static GuiItem action(Material material, String title, List<String> summary, String click,
                          Consumer<Player> action) {
        Card card = Card.of(KitStyle.HEX).tag(title).blank();
        summary.forEach(card::raw);
        card.blank().click(click);
        return new GuiItem(KitStyle.decorate(new ItemStack(material), Palette.heading(title), card.build(), false),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    Guis.click(player);
                    action.accept(player);
                });
    }

    private GuiItem iconButton(Kit kit, Runnable reopen) {
        List<String> lore = Card.of(KitStyle.HEX).tag("Icône").blank()
                .line("L'objet affiché dans le menu.")
                .blank()
                .click("pour utiliser l'objet en main")
                .build();
        return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), Palette.heading("Icône"), lore, false), event -> {
            Player player = (Player) event.getWhoClicked();
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                Guis.deny(player);
                Messages.send(player, "kits.hold-item");
                return;
            }
            Guis.success(player);
            editor.setIcon(kit.id(), held);
            reopen.run();
        });
    }

    private GuiItem categoryButton(Kit kit, Runnable reopen) {
        List<String> ids = new ArrayList<>();
        ids.add(null);
        ids.addAll(service.catalog().categories().keySet());
        Card card = Card.of(KitStyle.HEX).tag("Catégorie").blank();
        for (String category : ids) {
            String label = category == null ? "Aucune" : service.catalog().category(category)
                    .map(value -> Mini.plain(Mini.label(value.name()))).orElse(category);
            boolean selected = category == null ? kit.category() == null : category.equals(kit.category());
            card.option(selected, label);
        }
        card.blank().click("Clic gauche", "suivante").click("Clic droit", "précédente");
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.BOOKSHELF), Palette.heading("Catégorie"),
                card.build(), false), event -> {
            Player player = (Player) event.getWhoClicked();
            int index = Math.max(0, ids.indexOf(kit.category()));
            int step = event.isRightClick() ? -1 : 1;
            String next = ids.get(Math.floorMod(index + step, ids.size()));
            Guis.click(player);
            editor.setCategory(kit.id(), next);
            reopen.run();
        });
    }

    private GuiItem permissionButton(Kit kit, Runnable reopen) {
        String suggested = "loadout.kit." + kit.id();
        List<String> lore = Card.of(KitStyle.HEX).tag("Permission").blank()
                .stat(Card.FLAG, "Actuelle", kit.permission() == null ? "aucune, ouvert à tous" : kit.permission())
                .blank()
                .click("Clic gauche", "écrire une permission")
                .click("Clic droit", "utiliser " + suggested)
                .click("Shift clic", "retirer la permission")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.TRIPWIRE_HOOK), Palette.heading("Permission"),
                lore, kit.permission() != null), event -> {
            Player player = (Player) event.getWhoClicked();
            Guis.click(player);
            if (event.isShiftClick()) {
                editor.setPermission(kit.id(), null);
                reopen.run();
            } else if (event.isRightClick()) {
                editor.setPermission(kit.id(), suggested);
                reopen.run();
            } else {
                KitPrompts.text(player, "Permission", true, typed -> editor.setPermission(kit.id(),
                        typed.replace(" ", "")), reopen);
            }
        });
    }

    private GuiItem statsButton(Kit kit, Runnable reopen) {
        KitRepository.Stats stats = service.repository().stats(kit.id());
        Card card = Card.of(KitStyle.HEX).tag("Statistiques").blank()
                .count(Card.AMOUNT, "Récupérations", stats.claims())
                .count(Card.PLAYER, "Joueurs", stats.players())
                .stat(Card.TIME, "Dernière", stats.last() <= 0L ? "jamais"
                        : "il y a " + Numbers.duration(System.currentTimeMillis() - stats.last()));
        if (kit.limitedStock()) {
            card.stat(Card.ZONE, "Stock utilisé", stats.stockUsed() + " / " + kit.stock())
                    .blank().click("Shift clic", "pour remettre le stock à zéro");
        }
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.KNOWLEDGE_BOOK), Palette.heading("Statistiques"),
                card.build(), false), event -> {
            Player player = (Player) event.getWhoClicked();
            if (event.isShiftClick() && kit.limitedStock()) {
                service.repository().resetStock(kit.id());
                Guis.success(player);
                Messages.send(player, "kits.stock-reset", KitService.kitResolver(kit));
                reopen.run();
            }
        });
    }

    private GuiItem deleteButton(Kit kit, Runnable back, Runnable reopen) {
        List<String> lore = Card.of(Palette.ERROR_HEX).tag("Suppression").blank()
                .line("Supprime le kit et l'historique")
                .line("de récupération des joueurs.")
                .blank()
                .click("Shift clic", "pour supprimer")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.BARRIER), Palette.ERROR + "<b>Supprimer</b>",
                lore, false), event -> {
            Player player = (Player) event.getWhoClicked();
            if (!event.isShiftClick()) {
                Guis.deny(player);
                return;
            }
            Guis.click(player);
            ConfirmMenu.create("Supprimer un kit")
                    .subject(KitStyle.icon(kit))
                    .question("Supprimer " + kit.id() + " ?")
                    .confirmLabel("Supprimer")
                    .details(Card.of(Palette.ERROR_HEX).blank().deny("Action irréversible").build())
                    .onConfirm(viewer -> {
                        editor.delete(kit.id());
                        service.repository().forget(kit.id());
                        Messages.send(viewer, "kits.deleted", Mini.value("id", kit.id()));
                        if (back != null) {
                            back.run();
                        } else {
                            viewer.closeInventory();
                        }
                    })
                    .onCancel(viewer -> reopen.run())
                    .open(player);
        });
    }

    private static List<String> rewardSummary(Kit kit) {
        KitRewards rewards = kit.rewards();
        List<String> lines = new ArrayList<>();
        if (rewards.money() > 0.0D) {
            lines.add(Palette.TEXT + "Argent : " + Numbers.money(rewards.money()));
        }
        if (rewards.shards() > 0L) {
            lines.add(Palette.TEXT + "Fragments : " + rewards.shards());
        }
        if (rewards.levels() > 0) {
            lines.add(Palette.TEXT + "Niveaux : " + rewards.levels());
        }
        if (!rewards.keys().isEmpty()) {
            lines.add(Palette.TEXT + "Clés : " + rewards.keys().size() + " caisse(s)");
        }
        lines.add(Palette.TEXT + "Commandes : " + rewards.commands().size() + ", effets : " + rewards.effects().size());
        return lines;
    }

    private static List<String> conditionSummary(Kit kit) {
        KitRequirements requirements = kit.requirements();
        if (requirements.none()) {
            return List.of(Palette.MUTED + "Aucune");
        }
        List<String> lines = new ArrayList<>();
        if (requirements.playtime() > 0L) {
            lines.add(Palette.TEXT + "Temps de jeu : " + Numbers.duration(requirements.playtime()));
        }
        if (!requirements.kits().isEmpty()) {
            lines.add(Palette.TEXT + "Kits requis : " + String.join(", ", requirements.kits()));
        }
        if (!requirements.worlds().isEmpty()) {
            lines.add(Palette.TEXT + "Mondes : " + String.join(", ", requirements.worlds()));
        }
        if (!requirements.schedule().always()) {
            lines.add(Palette.TEXT + "Période limitée");
        }
        return lines;
    }

    private static List<String> costSummary(KitCost cost) {
        if (cost.free()) {
            return List.of(Palette.MUTED + "Gratuit");
        }
        List<String> lines = new ArrayList<>();
        if (cost.money() > 0.0D) {
            lines.add(Palette.TEXT + "Argent : " + Numbers.money(cost.money()));
        }
        if (cost.shards() > 0L) {
            lines.add(Palette.TEXT + "Fragments : " + cost.shards());
        }
        if (cost.levels() > 0) {
            lines.add(Palette.TEXT + "Niveaux : " + cost.levels());
        }
        return lines;
    }

    private static List<String> limitSummary(Kit kit) {
        List<String> lines = new ArrayList<>();
        lines.add(Palette.TEXT + "Recharge : " + (kit.cooldown() <= 0L ? "aucune" : Numbers.duration(kit.cooldown())));
        lines.add(Palette.TEXT + "Remise à zéro : " + KitStyle.resetLabel(kit));
        lines.add(Palette.TEXT + "Utilisations : " + (kit.limitedUses() ? kit.maxUses() : "illimitées"));
        lines.add(Palette.TEXT + "Stock : " + (kit.limitedStock() ? kit.stock() : "illimité"));
        return lines;
    }

    private static List<String> optionSummary(Kit kit) {
        List<String> lines = new ArrayList<>();
        for (KitOptions.Flag flag : KitOptions.Flag.values()) {
            if (kit.options().enabled(flag)) {
                lines.add(Palette.SUCCESS + Palette.CHECK + " " + Palette.TEXT + flag.label());
            }
        }
        return lines.isEmpty() ? List.of(Palette.MUTED + "Aucune") : lines;
    }

    private void options(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        Runnable reopen = () -> options(player, id, back);
        Gui gui = Gui.builder().rows(5).title(Mini.parse(Palette.smallTitle("Options du kit"))).create();
        Guis.fill(gui);
        KitOptions.Flag[] flags = KitOptions.Flag.values();
        boolean hidden = kit.hideLocked();
        gui.setItem(4, 9, new GuiItem(KitStyle.decorate(new ItemStack(hidden ? Material.ENDER_EYE : Material.ENDER_PEARL),
                (hidden ? Palette.SUCCESS : Palette.MUTED) + "<b>Masqué si verrouillé</b>", Card.of(KitStyle.HEX)
                        .tag("Option").blank()
                        .line("Les joueurs sans accès ne voient")
                        .line("pas du tout ce kit.")
                        .blank()
                        .option(hidden, "Activée")
                        .option(!hidden, "Désactivée")
                        .blank()
                        .click(hidden ? "pour désactiver" : "pour activer")
                        .build(), hidden), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            editor.setHideLocked(id, !hidden);
            reopen.run();
        }));
        for (int index = 0; index < flags.length; index++) {
            KitOptions.Flag flag = flags[index];
            boolean enabled = kit.options().enabled(flag);
            List<String> lore = Card.of(KitStyle.HEX).tag("Option").blank()
                    .line(flag.description())
                    .blank()
                    .option(enabled, "Activée")
                    .option(!enabled, "Désactivée")
                    .blank()
                    .click(enabled ? "pour désactiver" : "pour activer")
                    .build();
            int row = 2 + index / 5;
            int column = 1 + index % 5 * 2;
            gui.setItem(row, column, new GuiItem(KitStyle.decorate(new ItemStack(enabled ? Material.LIME_DYE
                    : Material.GRAY_DYE), (enabled ? Palette.SUCCESS : Palette.MUTED) + "<b>" + flag.label() + "</b>",
                    lore, enabled), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                editor.setOption(id, flag, !enabled);
                reopen.run();
            }));
        }
        gui.setItem(5, 5, ceremonyButton(kit, reopen));
        gui.setItem(5, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(5, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private GuiItem ceremonyButton(Kit kit, Runnable reopen) {
        boolean automatic = kit.animationLevel() == Kit.AUTOMATIC;
        KitShow current = KitShow.of(KitShow.levelFor(kit, 0));
        Card card = Card.of(KitStyle.HEX).tag("Cérémonie d'ouverture").blank()
                .stat(Card.STAR, "Réglage", automatic ? "automatique" : "fixé au niveau " + kit.animationLevel())
                .stat(Card.CHANCE, automatic ? "Niveau sans maîtrise" : "Niveau", current.level() + " · "
                        + current.name())
                .blank();
        if (automatic) {
            card.line("Le niveau monte avec la richesse du")
                    .line("kit et le palier de maîtrise du joueur.");
        } else {
            card.line("Tous les joueurs voient la même")
                    .line("cérémonie, la maîtrise ne change rien.");
        }
        card.blank();
        for (int level = 0; level <= KitShow.MAXIMUM; level++) {
            card.option(!automatic && kit.animationLevel() == level, "Niveau " + level + " · "
                    + KitShow.of(level).name());
        }
        card.option(automatic, "Automatique");
        card.blank()
                .click("Clic gauche", "niveau suivant")
                .click("Clic droit", "niveau précédent")
                .click("Shift clic", "revenir en automatique");
        return new GuiItem(KitStyle.decorate(new ItemStack(automatic ? Material.FIREWORK_ROCKET
                : Material.FIREWORK_STAR), Palette.heading("Cérémonie d'ouverture"), card.build(), !automatic),
                event -> {
                    Player viewer = (Player) event.getWhoClicked();
                    Guis.click(viewer);
                    if (event.isShiftClick()) {
                        editor.setAnimationLevel(kit.id(), Kit.AUTOMATIC);
                    } else {
                        int steps = KitShow.MAXIMUM + 2;
                        int position = automatic ? 0 : kit.animationLevel() + 1;
                        int next = Math.floorMod(position + (event.isRightClick() ? -1 : 1), steps);
                        editor.setAnimationLevel(kit.id(), next == 0 ? Kit.AUTOMATIC : next - 1);
                    }
                    reopen.run();
                });
    }

    private void limits(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        Runnable reopen = () -> limits(player, id, back);
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle("Recharge et limites"))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, cleared(Material.CLOCK, "Recharge", kit.cooldown() <= 0L ? "aucune"
                        : Numbers.duration(kit.cooldown()), "Exemples : 30m, 12h, 1j 6h",
                viewer -> KitPrompts.duration(viewer, "Durée ex 1j 2h", millis -> editor.setCooldown(id, millis),
                        reopen), viewer -> {
                    editor.setCooldown(id, 0L);
                    reopen.run();
                }));
        gui.setItem(2, 4, new GuiItem(KitStyle.decorate(new ItemStack(Material.DAYLIGHT_DETECTOR),
                Palette.heading("Remise à zéro"), Card.of(KitStyle.HEX).tag("Calendrier").blank()
                        .stat(Card.TIME, "Actuelle", KitStyle.resetLabel(kit))
                        .blank()
                        .line("Le kit revient à heure fixe, même")
                        .line("sans attendre la recharge complète.")
                        .blank()
                        .click("Clic gauche", "changer de fréquence")
                        .click("Clic droit", "choisir l'heure")
                        .click("Shift clic", "choisir le jour")
                        .build(), kit.reset() != KitReset.NONE), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            if (event.isShiftClick()) {
                DayOfWeek next = kit.resetDay().plus(1);
                editor.setResetDay(id, next);
                reopen.run();
            } else if (event.isRightClick()) {
                ChatPrompts.open(viewer, "l'heure, par exemple 18:00", typed -> {
                    LocalTime time = KitSchedule.parseTime(typed);
                    if (time == null) {
                        KitPrompts.invalid(viewer, typed);
                    } else {
                        editor.setResetTime(id, time);
                    }
                    reopen.run();
                });
            } else {
                editor.setReset(id, kit.reset().next());
                reopen.run();
            }
        }));
        gui.setItem(2, 6, cleared(Material.REPEATER, "Utilisations maximum", kit.limitedUses()
                        ? String.valueOf(kit.maxUses()) : "illimitées", "1 pour un kit unique par joueur",
                viewer -> KitPrompts.integer(viewer, "Nombre max", 1, 1_000_000, value -> editor.setMaxUses(id, value),
                        reopen), viewer -> {
                    editor.setMaxUses(id, 0);
                    reopen.run();
                }));
        gui.setItem(2, 8, cleared(Material.BARREL, "Stock serveur", kit.limitedStock() ? String.valueOf(kit.stock())
                        : "illimité", "Nombre total pour tout le serveur",
                viewer -> KitPrompts.integer(viewer, "Stock total", 1, 1_000_000, value -> editor.setStock(id, value),
                        reopen), viewer -> {
                    editor.setStock(id, 0);
                    reopen.run();
                }));
        gui.setItem(3, 3, cleared(Material.WITHER_ROSE, "Objets temporaires", kit.lifetime() <= 0L ? "permanents"
                        : "disparaissent après " + Numbers.duration(kit.lifetime()), "Les objets du kit s'effacent seuls",
                viewer -> KitPrompts.duration(viewer, "Durée ex 2h", millis -> editor.setLifetime(id, millis), reopen),
                viewer -> {
                    editor.setLifetime(id, 0L);
                    reopen.run();
                }));
        gui.setItem(3, 7, action(Material.NETHER_STAR, "Maîtrise", List.of(Palette.TEXT
                        + (kit.mastery().enabled() ? kit.mastery().tiers().size() + " palier(s) propres"
                        : kit.options().mastery() ? "modèle par défaut" : "désactivée")), "pour éditer les paliers",
                viewer -> {
                    if (progression != null) {
                        progression.tiers(viewer, id, reopen);
                    }
                }));
        gui.setItem(3, 5, action(Material.EXPERIENCE_BOTTLE, "Réductions de recharge", kit.reductions().isEmpty()
                ? List.of(Palette.MUTED + "Aucune") : kit.reductions().entrySet().stream()
                .map(entry -> Palette.TEXT + entry.getKey() + " : -" + entry.getValue() + "%").toList(),
                "pour modifier", viewer -> KitListMenu.open(viewer, new KitListMenu.Spec("Réductions",
                        List.of("Une permission réduit la recharge", "d'un pourcentage, ex loadout.kit.vip:25"),
                        "permission:25", true, () -> service.kit(id).map(value -> value.reductions().entrySet()
                        .stream().map(entry -> entry.getKey() + ":" + entry.getValue()).toList()).orElse(List.of()),
                        lines -> editor.setReductions(id, parseReductions(lines)),
                        KitEditorMenu::validateReduction, Material.EXPERIENCE_BOTTLE), reopen)));
        gui.setItem(4, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(4, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    static Map<String, Integer> parseReductions(List<String> lines) {
        return KitLoader.reductions(lines, "editeur", new ArrayList<>());
    }

    static String validateReduction(String line) {
        return parseReductions(List.of(line.replace(" ", ""))).isEmpty() ? "invalide" : null;
    }

    private static GuiItem cleared(Material material, String title, String value, String hint,
                                   Consumer<Player> edit, Consumer<Player> clear) {
        List<String> lore = Card.of(KitStyle.HEX).tag(title).blank()
                .stat(Card.FLAG, "Actuel", value)
                .line(Palette.MUTED + hint)
                .blank()
                .click("Clic gauche", "modifier")
                .click("Shift clic", "retirer")
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(material), Palette.heading(title), lore, false), event -> {
            Player player = (Player) event.getWhoClicked();
            Guis.click(player);
            if (event.isShiftClick()) {
                clear.accept(player);
            } else {
                edit.accept(player);
            }
        });
    }

    private void cost(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        Runnable reopen = () -> cost(player, id, back);
        Gui gui = Gui.builder().rows(3).title(Mini.parse(Palette.smallTitle("Prix du kit"))).create();
        Guis.fill(gui);
        gui.setItem(2, 3, cleared(Material.GOLD_INGOT, "Argent", Numbers.money(kit.cost().money()),
                "Retiré du solde à chaque récupération",
                viewer -> KitPrompts.amount(viewer, "Montant", value -> editor.setCostMoney(id, value), reopen),
                viewer -> {
                    editor.setCostMoney(id, 0.0D);
                    reopen.run();
                }));
        gui.setItem(2, 5, cleared(Material.AMETHYST_SHARD, "Fragments", Numbers.count(kit.cost().shards()),
                "Fragments du Rift retirés",
                viewer -> KitPrompts.integer(viewer, "Fragments", 1, Integer.MAX_VALUE,
                        value -> editor.setCostShards(id, value), reopen),
                viewer -> {
                    editor.setCostShards(id, 0L);
                    reopen.run();
                }));
        gui.setItem(2, 7, cleared(Material.EXPERIENCE_BOTTLE, "Niveaux", String.valueOf(kit.cost().levels()),
                "Niveaux d'expérience retirés",
                viewer -> KitPrompts.integer(viewer, "Niveaux", 1, 10_000, value -> editor.setCostLevels(id, value),
                        reopen),
                viewer -> {
                    editor.setCostLevels(id, 0);
                    reopen.run();
                }));
        gui.setItem(3, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(3, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void rewards(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        KitRewards rewards = kit.rewards();
        Runnable reopen = () -> rewards(player, id, back);
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle("Récompenses du kit"))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, cleared(Material.GOLD_BLOCK, "Argent", Numbers.money(rewards.money()), "Versé sur le solde",
                viewer -> KitPrompts.amount(viewer, "Montant", value -> editor.setRewardMoney(id, value), reopen),
                viewer -> {
                    editor.setRewardMoney(id, 0.0D);
                    reopen.run();
                }));
        gui.setItem(2, 3, cleared(Material.AMETHYST_CLUSTER, "Fragments", Numbers.count(rewards.shards()),
                "Fragments du Rift offerts",
                viewer -> KitPrompts.integer(viewer, "Fragments", 1, Integer.MAX_VALUE,
                        value -> editor.setRewardShards(id, value), reopen),
                viewer -> {
                    editor.setRewardShards(id, 0L);
                    reopen.run();
                }));
        gui.setItem(2, 4, cleared(Material.EXPERIENCE_BOTTLE, "Niveaux", String.valueOf(rewards.levels()),
                "Niveaux d'expérience offerts",
                viewer -> KitPrompts.integer(viewer, "Niveaux", 1, 10_000, value -> editor.setRewardLevels(id, value),
                        reopen),
                viewer -> {
                    editor.setRewardLevels(id, 0);
                    reopen.run();
                }));
        gui.setItem(2, 5, action(Material.TRIPWIRE_HOOK, "Clés de caisse", rewards.keys().isEmpty()
                ? List.of(Palette.MUTED + "Aucune") : rewards.keys().entrySet().stream()
                .map(entry -> Palette.TEXT + entry.getValue() + "x " + actions.crateName(entry.getKey())).toList(),
                "pour choisir", viewer -> keys(viewer, id, reopen)));
        gui.setItem(2, 6, action(Material.COMMAND_BLOCK, "Commandes", preview(rewards.commands().stream()
                        .map(KitDispatch::write).toList()), "pour modifier",
                viewer -> KitListMenu.open(viewer, new KitListMenu.Spec("Commandes", List.of(
                        "console: commande lancée par la console", "joueur: commande lancée par le joueur",
                        "Variables : <player>, <uuid>, <kit>"), "console: say <player>", true,
                        () -> service.kit(id).map(value -> value.rewards().commands().stream()
                                .map(KitDispatch::write).toList()).orElse(List.of()),
                        lines -> editor.setRewardList(id, "commands", lines), null, Material.COMMAND_BLOCK), reopen)));
        gui.setItem(2, 7, action(Material.POTION, "Effets", preview(rewards.effects().stream()
                        .map(KitEffect::label).toList()), "pour modifier",
                viewer -> KitListMenu.open(viewer, new KitListMenu.Spec("Effets", List.of(
                        "effet:durée:niveau", "ex speed:2m:2 ou strength:30s:1"), "speed:1m:2", false,
                        () -> service.kit(id).map(value -> value.rewards().effects().stream()
                                .map(KitEffect::write).toList()).orElse(List.of()),
                        lines -> editor.setRewardList(id, "effects", lines),
                        line -> KitEffect.parse(line) == null ? "invalide" : null, Material.POTION), reopen)));
        gui.setItem(2, 8, action(Material.OAK_SIGN, "Messages", preview(rewards.messages()), "pour modifier",
                viewer -> KitListMenu.open(viewer, new KitListMenu.Spec("Messages", List.of(
                        "Envoyés au joueur à la récupération.", "Variables : <player>, <kit>"),
                        "Message", true, () -> service.kit(id).map(value -> value.rewards().messages())
                        .orElse(List.of()), lines -> editor.setRewardList(id, "messages", lines), null,
                        Material.OAK_SIGN), reopen)));
        gui.setItem(3, 5, action(Material.FILLED_MAP, "Lignes bonus", preview(rewards.lines()), "pour modifier",
                viewer -> KitListMenu.open(viewer, new KitListMenu.Spec("Lignes bonus", List.of(
                        "Décrivent aux joueurs ce que font", "les commandes, ex Grade VIP 7 jours."),
                        "Ligne bonus", true, () -> service.kit(id).map(value -> value.rewards().lines())
                        .orElse(List.of()), lines -> editor.setRewardList(id, "lines", lines), null,
                        Material.FILLED_MAP), reopen)));
        gui.setItem(4, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(4, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void keys(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        Runnable reopen = () -> keys(player, id, back);
        Gui gui = Gui.builder().rows(6).title(Mini.parse(Palette.smallTitle("Clés offertes"))).create();
        Guis.fill(gui);
        List<CrateBridge.KeyCrate> list = crates.crates();
        for (int index = 0; index < Math.min(45, list.size()); index++) {
            CrateBridge.KeyCrate crate = list.get(index);
            int amount = kit.rewards().keys().getOrDefault(crate.id(), 0);
            List<String> lore = Card.of(KitStyle.HEX).tag("Caisse").blank()
                    .count(Card.AMOUNT, "Clés offertes", amount)
                    .blank()
                    .click("Clic gauche", "+1")
                    .click("Clic droit", "-1")
                    .click("Shift clic", "écrire un nombre")
                    .build();
            gui.setItem(index, new GuiItem(KitStyle.decorate(new ItemStack(Material.TRIPWIRE_HOOK),
                    crate.displayName(), lore, amount > 0), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                ClickType click = event.getClick();
                if (click.isShiftClick()) {
                    KitPrompts.integer(viewer, "Nombre de clés", 0, 10_000,
                            value -> editor.setRewardKeys(id, crate.id(), value), reopen);
                    return;
                }
                editor.setRewardKeys(id, crate.id(), Math.max(0, amount + (click.isRightClick() ? -1 : 1)));
                reopen.run();
            }));
        }
        gui.setItem(6, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(6, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void requirements(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        KitRequirements requirements = kit.requirements();
        KitSchedule schedule = requirements.schedule();
        ZoneId zone = service.settings().zone();
        Runnable reopen = () -> requirements(player, id, back);
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle("Conditions du kit"))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, cleared(Material.CLOCK, "Temps de jeu", requirements.playtime() <= 0L ? "aucun"
                        : Numbers.duration(requirements.playtime()), "Temps total passé sur le serveur",
                viewer -> KitPrompts.duration(viewer, "Durée ex 5h", millis -> editor.setPlaytime(id, millis), reopen),
                viewer -> {
                    editor.setPlaytime(id, 0L);
                    reopen.run();
                }));
        gui.setItem(2, 3, action(Material.CHEST_MINECART, "Kits requis", requirements.kits().isEmpty()
                        ? List.of(Palette.MUTED + "Aucun") : requirements.kits().stream().map(value -> Palette.TEXT + value)
                        .toList(), "pour choisir", viewer -> requiredKits(viewer, id, reopen)));
        gui.setItem(2, 4, action(Material.GRASS_BLOCK, "Mondes", requirements.worlds().isEmpty()
                        ? List.of(Palette.MUTED + "Tous") : requirements.worlds().stream().map(value -> Palette.TEXT + value)
                        .toList(), "pour choisir", viewer -> worlds(viewer, id, reopen)));
        gui.setItem(2, 5, cleared(Material.GOLD_NUGGET, "Solde minimum", Numbers.money(requirements.balance()),
                "Le joueur doit posséder ce montant",
                viewer -> KitPrompts.amount(viewer, "Montant", value -> editor.setMinimumBalance(id, value), reopen),
                viewer -> {
                    editor.setMinimumBalance(id, 0.0D);
                    reopen.run();
                }));
        gui.setItem(2, 6, cleared(Material.ENCHANTING_TABLE, "Niveau minimum", String.valueOf(requirements.level()),
                "Niveau d'expérience à atteindre",
                viewer -> KitPrompts.integer(viewer, "Niveau", 1, 10_000, value -> editor.setMinimumLevel(id, value),
                        reopen),
                viewer -> {
                    editor.setMinimumLevel(id, 0);
                    reopen.run();
                }));
        gui.setItem(2, 7, action(Material.DIAMOND_SWORD, "Défis", requirements.statistics().isEmpty()
                        ? List.of(Palette.MUTED + "Aucun") : requirements.statistics().stream()
                        .map(value -> Palette.TEXT + value.label() + " : " + Numbers.count(value.amount())).toList(),
                "pour modifier", viewer -> KitListMenu.open(viewer, new KitListMenu.Spec("Défis", List.of(
                        "statistique:nombre ou statistique:cible:nombre",
                        "ex mob_kills:100, mine_block:diamond_ore:50,", "kill_entity:zombie:30, fish_caught:20"),
                        "mob_kills:100", true, () -> service.kit(id).map(value -> value.requirements().statistics()
                        .stream().map(KitStatistic::write).toList()).orElse(List.of()),
                        lines -> editor.setStatistics(id, lines),
                        line -> KitStatistic.parse(line) == null ? "invalide" : null, Material.DIAMOND_SWORD), reopen)));
        gui.setItem(3, 3, cleared(Material.LIME_BANNER, "Ouverture", schedule.from() <= 0L ? "immédiate"
                        : KitSchedule.writeDate(schedule.from(), zone), "Format jj/mm/aaaa hh:mm",
                viewer -> ChatPrompts.open(viewer, "la date au format jj/mm/aaaa hh:mm", typed -> {
                    long at = KitSchedule.parseDate(typed, zone);
                    if (at <= 0L) {
                        KitPrompts.invalid(viewer, typed);
                    } else {
                        editor.setFrom(id, at, zone);
                    }
                    reopen.run();
                }), viewer -> {
                    editor.setFrom(id, 0L, zone);
                    reopen.run();
                }));
        gui.setItem(3, 4, cleared(Material.RED_BANNER, "Fermeture", schedule.until() <= 0L ? "jamais"
                        : KitSchedule.writeDate(schedule.until(), zone), "Format jj/mm/aaaa hh:mm",
                viewer -> ChatPrompts.open(viewer, "la date au format jj/mm/aaaa hh:mm", typed -> {
                    long at = KitSchedule.parseDate(typed, zone);
                    if (at <= 0L) {
                        KitPrompts.invalid(viewer, typed);
                    } else {
                        editor.setUntil(id, at, zone);
                    }
                    reopen.run();
                }), viewer -> {
                    editor.setUntil(id, 0L, zone);
                    reopen.run();
                }));
        gui.setItem(3, 6, action(Material.PAINTING, "Jours", schedule.days().isEmpty() ? List.of(Palette.MUTED
                + "Tous les jours") : List.of(Palette.TEXT + KitSchedule.dayList(schedule.days())), "pour choisir",
                viewer -> days(viewer, id, reopen)));
        gui.setItem(3, 7, cleared(Material.SUNFLOWER, "Plage horaire", schedule.opens() == null ? "toute la journée"
                        : schedule.writeHours(), "Format 18:00-23:00",
                viewer -> ChatPrompts.open(viewer, "la plage horaire, par exemple 18:00-23:00", typed -> {
                    LocalTime[] hours = KitSchedule.parseHours(typed.replace(" ", ""));
                    if (hours == null) {
                        KitPrompts.invalid(viewer, typed);
                    } else {
                        editor.setHours(id, hours[0], hours[1]);
                    }
                    reopen.run();
                }), viewer -> {
                    editor.setHours(id, null, null);
                    reopen.run();
                }));
        gui.setItem(4, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(4, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void requiredKits(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        Runnable reopen = () -> requiredKits(player, id, back);
        Gui gui = Gui.builder().rows(6).title(Mini.parse(Palette.smallTitle("Kits requis"))).create();
        Guis.fill(gui);
        int index = 0;
        for (Kit other : service.catalog().kits().values()) {
            if (other.id().equals(id) || index >= 45) {
                continue;
            }
            boolean selected = kit.requirements().kits().contains(other.id());
            gui.setItem(index++, new GuiItem(KitStyle.decorate(KitStyle.icon(other), other.name(),
                    Card.of(KitStyle.HEX).blank().option(selected, "Requis").blank()
                            .click(selected ? "pour ne plus l'exiger" : "pour l'exiger").build(), selected), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                List<String> updated = new ArrayList<>(kit.requirements().kits());
                if (selected) {
                    updated.remove(other.id());
                } else {
                    updated.add(other.id());
                }
                editor.setRequiredKits(id, updated);
                reopen.run();
            }));
        }
        gui.setItem(6, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(6, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void worlds(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        Runnable reopen = () -> worlds(player, id, back);
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle("Mondes autorisés"))).create();
        Guis.fill(gui);
        List<World> worlds = Bukkit.getWorlds();
        for (int index = 0; index < Math.min(27, worlds.size()); index++) {
            String name = worlds.get(index).getName();
            boolean selected = kit.requirements().worlds().contains(name.toLowerCase(Locale.ROOT));
            Material icon = switch (worlds.get(index).getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            gui.setItem(index, new GuiItem(KitStyle.decorate(new ItemStack(icon), Palette.heading(name),
                    Card.of(KitStyle.HEX).blank().option(selected, "Autorisé").blank()
                            .line("Sans aucun monde coché, le kit")
                            .line("se récupère partout.")
                            .blank().click("pour basculer").build(), selected), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                List<String> updated = new ArrayList<>(kit.requirements().worlds());
                if (selected) {
                    updated.remove(name.toLowerCase(Locale.ROOT));
                } else {
                    updated.add(name.toLowerCase(Locale.ROOT));
                }
                editor.setWorlds(id, updated);
                reopen.run();
            }));
        }
        gui.setItem(4, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(4, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void days(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        Runnable reopen = () -> days(player, id, back);
        Gui gui = Gui.builder().rows(3).title(Mini.parse(Palette.smallTitle("Jours d'ouverture"))).create();
        Guis.fill(gui);
        Set<DayOfWeek> current = kit.requirements().schedule().days();
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean selected = current.contains(day);
            String name = KitSchedule.dayName(day);
            gui.setItem(2, 1 + day.getValue(), new GuiItem(KitStyle.decorate(new ItemStack(selected
                            ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE),
                    (selected ? Palette.SUCCESS : Palette.MUTED) + "<b>" + Character.toUpperCase(name.charAt(0))
                            + name.substring(1) + "</b>",
                    Card.of(KitStyle.HEX).blank().option(selected, "Ouvert").blank().click("pour basculer").build(),
                    selected), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                Set<DayOfWeek> updated = current.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(current);
                if (selected) {
                    updated.remove(day);
                } else {
                    updated.add(day);
                }
                editor.setDays(id, updated);
                reopen.run();
            }));
        }
        gui.setItem(3, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(3, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void pool(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        KitPool pool = kit.pool();
        Runnable reopen = () -> pool(player, id, back);
        Gui gui = Gui.builder().rows(6).title(Mini.parse(Palette.smallTitle("Tirage aléatoire"))).create();
        Guis.fill(gui);
        List<KitPool.Entry> entries = pool.entries();
        for (int index = 0; index < Math.min(45, entries.size()); index++) {
            gui.setItem(index, poolEntry(id, pool, entries.get(index), reopen));
        }
        gui.setItem(6, 2, action(Material.LIME_DYE, "Ajouter l'objet en main", List.of(
                        Palette.TEXT + "Poids 10, quantité de l'objet tenu."), "pour ajouter",
                viewer -> {
                    ItemStack held = viewer.getInventory().getItemInMainHand();
                    if (held.getType().isAir()) {
                        Guis.deny(viewer);
                        Messages.send(viewer, "kits.hold-item");
                        return;
                    }
                    editor.addPoolEntry(id, held.clone());
                    reopen.run();
                }));
        gui.setItem(6, 3, action(Material.SHULKER_BOX, "Importer une shulker", List.of(
                        Palette.TEXT + "Chaque objet de la boîte tenue", Palette.TEXT + "devient une entrée."),
                "pour importer", viewer -> {
                    List<ItemStack> contents = Shulkers.contentsOf(viewer.getInventory().getItemInMainHand());
                    if (contents.isEmpty()) {
                        Guis.deny(viewer);
                        Messages.send(viewer, "kits.empty-container");
                        return;
                    }
                    for (ItemStack item : contents) {
                        editor.addPoolEntry(id, item);
                    }
                    Messages.send(viewer, "kits.imported", Mini.value("amount", String.valueOf(contents.size())));
                    reopen.run();
                }));
        gui.setItem(6, 5, cleared(Material.ENDER_EYE, "Tirages", String.valueOf(pool.rolls()),
                "Objets tirés à chaque récupération",
                viewer -> KitPrompts.integer(viewer, "Tirages", 0, KitPool.MAXIMUM_ROLLS,
                        value -> editor.setPoolRolls(id, value), reopen),
                viewer -> {
                    editor.setPoolRolls(id, 0);
                    reopen.run();
                }));
        gui.setItem(6, 7, new GuiItem(KitStyle.decorate(new ItemStack(pool.unique() ? Material.AMETHYST_SHARD
                        : Material.QUARTZ), Palette.heading("Doublons"), Card.of(KitStyle.HEX).blank()
                        .option(pool.unique(), "Chaque objet sort une seule fois")
                        .option(!pool.unique(), "Un objet peut sortir plusieurs fois")
                        .blank().click("pour basculer").build(), pool.unique()), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            editor.setPoolUnique(id, !pool.unique());
            reopen.run();
        }));
        gui.setItem(6, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(6, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private GuiItem poolEntry(String id, KitPool pool, KitPool.Entry entry, Runnable reopen) {
        ItemStack base = entry.item() == null ? new ItemStack(Material.BARRIER) : entry.item().clone();
        base.setAmount(1);
        String amount = entry.minimum() == entry.maximum() ? String.valueOf(entry.minimum())
                : entry.minimum() + " à " + entry.maximum();
        Card card = Card.of(KitStyle.HEX).tag("Entrée " + entry.id()).blank()
                .count(Card.CHANCE, "Poids", entry.weight())
                .stat(Card.CHANCE, pool.exact() ? "Chance" : "Part", KitPreviewMenu.percent(pool.exact()
                        ? pool.chance(entry) : pool.share(entry)))
                .stat(Card.AMOUNT, "Quantité", amount)
                .blank()
                .click("Clic gauche", "changer le poids")
                .click("Clic droit", "changer la quantité, ex 2-5")
                .click("Shift clic droit", "retirer");
        return new GuiItem(KitStyle.decorate(base, Palette.heading(entry.id()), card.build(), false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            ClickType click = event.getClick();
            if (click == ClickType.SHIFT_RIGHT) {
                editor.removePoolEntry(id, entry.id());
                reopen.run();
            } else if (click.isRightClick()) {
                ChatPrompts.open(viewer, "la plage min-max, par exemple 2-5", typed -> {
                    int[] range = range(typed);
                    if (range == null) {
                        KitPrompts.invalid(viewer, typed);
                    } else {
                        editor.setPoolAmounts(id, entry.id(), range[0], range[1]);
                    }
                    reopen.run();
                });
            } else {
                KitPrompts.integer(viewer, "Poids", 1, 1_000_000, value -> editor.setPoolWeight(id, entry.id(), value),
                        reopen);
            }
        });
    }

    static int[] range(String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        String[] parts = typed.replace(" ", "").split("-");
        try {
            int minimum = Integer.parseInt(parts[0]);
            int maximum = parts.length > 1 ? Integer.parseInt(parts[1]) : minimum;
            if (minimum < 1 || maximum < minimum || maximum > 6_400) {
                return null;
            }
            return new int[]{minimum, maximum};
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
