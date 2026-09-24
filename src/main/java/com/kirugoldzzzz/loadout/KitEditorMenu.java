package com.kirugoldzzzz.loadout;

import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.gui.ChatPrompts;
import com.kirugoldzzzz.loadout.common.gui.ConfirmMenu;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.item.ItemReturn;
import com.kirugoldzzzz.loadout.common.item.Shulkers;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.common.text.Tr;
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
                .title(Mini.parse(Palette.smallTitle(Tr.t("Éditer ") + kit.id())))
                .create();
        Guis.fill(gui);

        gui.setItem(1, 5, header(kit));

        gui.setItem(2, 2, action(Material.NAME_TAG, Tr.t("Nom"), List.of(Palette.TEXT + Tr.t("Actuel : ") + kit.name()),
                Tr.t("pour renommer"), viewer -> KitPrompts.text(viewer, Tr.t("Nouveau nom"), true,
                        typed -> editor.setName(id, typed), reopen)));
        gui.setItem(2, 3, action(Material.WRITABLE_BOOK, Tr.t("Description"), preview(kit.description()),
                Tr.t("pour modifier les lignes"), viewer -> KitListMenu.open(viewer, new KitListMenu.Spec(Tr.t("Description"),
                        List.of(Tr.t("Texte affiché sous le nom du kit.")), Tr.t("Ligne de texte"), true, () -> service.kit(id)
                        .map(Kit::description).orElse(List.of()), lines -> editor.setDescription(id, lines), null,
                        Material.PAPER), reopen)));
        gui.setItem(2, 4, iconButton(kit, reopen));
        gui.setItem(2, 5, categoryButton(kit, reopen));
        gui.setItem(2, 6, action(Material.COMPARATOR, Tr.t("Ordre"), List.of(Palette.TEXT + Tr.t("Position : ") + kit.order()),
                Tr.t("pour changer la position"), viewer -> KitPrompts.integer(viewer, Tr.t("Position"), -1000, 1000,
                        value -> editor.setOrder(id, value), reopen)));
        gui.setItem(2, 7, permissionButton(kit, reopen));
        gui.setItem(2, 8, action(Material.MAP, Tr.t("Indice de déblocage"), preview(kit.hint()),
                Tr.t("pour modifier"), viewer -> KitListMenu.open(viewer, new KitListMenu.Spec(Tr.t("Indice de déblocage"),
                        List.of(Tr.t("Affiché quand le kit est verrouillé,"), Tr.t("par exemple le grade à acheter.")),
                        Tr.t("Ligne d'indice"), true, () -> service.kit(id).map(Kit::hint).orElse(List.of()),
                        lines -> editor.setHint(id, lines), null, Material.PAPER), reopen)));

        gui.setItem(3, 2, action(Material.CHEST, Tr.t("Contenu"), List.of(Palette.TEXT + kit.contents().size()
                        + Tr.t(" objet(s) placés")), Tr.t("pour éditer comme un coffre"),
                viewer -> new KitContentsEditor(editor, kit, reopen).open(viewer)));
        gui.setItem(3, 3, action(Material.ENDER_EYE, Tr.t("Tirage aléatoire"), List.of(Palette.TEXT
                        + kit.pool().effectiveRolls() + Tr.t(" tirage(s) parmi ") + kit.pool().entries().size()),
                Tr.t("pour configurer"), viewer -> pool(viewer, id, reopen)));
        gui.setItem(3, 4, action(Material.GOLD_INGOT, Tr.t("Récompenses"), rewardSummary(kit), Tr.t("pour configurer"),
                viewer -> rewards(viewer, id, reopen)));
        gui.setItem(3, 5, action(Material.IRON_BARS, Tr.t("Conditions"), conditionSummary(kit), Tr.t("pour configurer"),
                viewer -> requirements(viewer, id, reopen)));
        gui.setItem(3, 6, action(Material.EMERALD, Tr.t("Prix"), costSummary(kit.cost()), Tr.t("pour configurer"),
                viewer -> cost(viewer, id, reopen)));
        gui.setItem(3, 7, action(Material.CLOCK, Tr.t("Recharge et limites"), limitSummary(kit), Tr.t("pour configurer"),
                viewer -> limits(viewer, id, reopen)));
        gui.setItem(3, 8, action(Material.REDSTONE_TORCH, Tr.t("Options"), optionSummary(kit), Tr.t("pour configurer"),
                viewer -> options(viewer, id, reopen)));

        gui.setItem(4, 2, action(Material.ARMOR_STAND, Tr.t("Copier mon inventaire"), List.of(
                        Palette.TEXT + Tr.t("Le contenu devient une copie exacte"), Palette.TEXT + Tr.t("de votre inventaire.")),
                Tr.t("pour copier"), viewer -> {
                    int captured = capture(viewer, id);
                    Messages.send(viewer, "kits.captured", Mini.value("id", id),
                            Mini.value("amount", String.valueOf(captured)));
                    reopen.run();
                }));
        gui.setItem(4, 3, action(Material.HOPPER, Tr.t("Recevoir le contenu"), List.of(
                        Palette.TEXT + Tr.t("Donne une copie des objets du kit"), Palette.TEXT + Tr.t("pour les retoucher.")),
                Tr.t("pour recevoir"), viewer -> {
                    ItemReturn.give(viewer, kit.contents().values(), Tr.t("Contenu de kit"));
                    Messages.send(viewer, "kits.loaded", Mini.value("id", id));
                    reopen.run();
                }));
        gui.setItem(4, 4, action(Material.PAPER, Tr.t("Recevoir un bon"), List.of(
                        Palette.TEXT + Tr.t("Un bon physique de ce kit.")), Tr.t("pour recevoir"),
                viewer -> {
                    service.giveVouchers(viewer, kit, 1, viewer.getName());
                    reopen.run();
                }));
        gui.setItem(4, 5, action(Material.SPYGLASS, Tr.t("Aperçu joueur"), List.of(
                        Palette.TEXT + Tr.t("Le kit tel que les joueurs le voient.")), Tr.t("pour ouvrir"),
                viewer -> preview.open(viewer, kit, reopen)));
        gui.setItem(4, 6, action(Material.TOTEM_OF_UNDYING, Tr.t("Tester"), List.of(
                        Palette.TEXT + Tr.t("Vous donne le kit sans payer ni"), Palette.TEXT + Tr.t("compter de récupération.")),
                Tr.t("pour tester"), viewer -> {
                    viewer.closeInventory();
                    actions.report(viewer, service.claim(viewer, kit, KitService.Source.ADMIN));
                }));
        gui.setItem(4, 7, statsButton(kit, reopen));
        gui.setItem(4, 8, action(Material.STRUCTURE_VOID, Tr.t("Dupliquer"), List.of(
                        Palette.TEXT + Tr.t("Crée une copie modifiable.")), Tr.t("pour dupliquer"),
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
                .stat(Card.CATEGORY, Tr.t("Catégorie"), kit.category() == null ? Tr.t("aucune") : kit.category())
                .stat(Card.FLAG, Tr.t("Permission"), kit.permission() == null ? Tr.t("aucune") : kit.permission())
                .count(Card.AMOUNT, Tr.t("Objets"), kit.contents().size())
                .build();
        return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), kit.name(), lore, true),
                event -> event.setCancelled(true));
    }

    private static List<String> preview(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of(Palette.MUTED + Tr.t("Vide"));
        }
        List<String> shown = new ArrayList<>();
        for (int index = 0; index < Math.min(4, lines.size()); index++) {
            shown.add(Palette.TEXT + lines.get(index));
        }
        if (lines.size() > 4) {
            shown.add(Palette.MUTED + Tr.t("et ") + (lines.size() - 4) + Tr.t(" autre(s)"));
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
        List<String> lore = Card.of(KitStyle.HEX).tag(Tr.t("Icône")).blank()
                .line(Tr.t("L'objet affiché dans le menu."))
                .blank()
                .click(Tr.t("pour utiliser l'objet en main"))
                .build();
        return new GuiItem(KitStyle.decorate(KitStyle.icon(kit), Palette.heading(Tr.t("Icône")), lore, false), event -> {
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
        Card card = Card.of(KitStyle.HEX).tag(Tr.t("Catégorie")).blank();
        for (String category : ids) {
            String label = category == null ? Tr.t("Aucune") : service.catalog().category(category)
                    .map(value -> Mini.plain(Mini.label(value.name()))).orElse(category);
            boolean selected = category == null ? kit.category() == null : category.equals(kit.category());
            card.option(selected, label);
        }
        card.blank().click(Tr.t("Clic gauche"), Tr.t("suivante")).click(Tr.t("Clic droit"), Tr.t("précédente"));
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.BOOKSHELF), Palette.heading(Tr.t("Catégorie")),
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
        List<String> lore = Card.of(KitStyle.HEX).tag(Tr.t("Permission")).blank()
                .stat(Card.FLAG, Tr.t("Actuelle"), kit.permission() == null ? Tr.t("aucune, ouvert à tous") : kit.permission())
                .blank()
                .click(Tr.t("Clic gauche"), Tr.t("écrire une permission"))
                .click(Tr.t("Clic droit"), Tr.t("utiliser ") + suggested)
                .click(Tr.t("Shift clic"), Tr.t("retirer la permission"))
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.TRIPWIRE_HOOK), Palette.heading(Tr.t("Permission")),
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
                KitPrompts.text(player, Tr.t("Permission"), true, typed -> editor.setPermission(kit.id(),
                        typed.replace(" ", "")), reopen);
            }
        });
    }

    private GuiItem statsButton(Kit kit, Runnable reopen) {
        KitRepository.Stats stats = service.repository().stats(kit.id());
        Card card = Card.of(KitStyle.HEX).tag(Tr.t("Statistiques")).blank()
                .count(Card.AMOUNT, Tr.t("Récupérations"), stats.claims())
                .count(Card.PLAYER, Tr.t("Joueurs"), stats.players())
                .stat(Card.TIME, Tr.t("Dernière"), stats.last() <= 0L ? Tr.t("jamais")
                        : "il y a " + Numbers.duration(System.currentTimeMillis() - stats.last()));
        if (kit.limitedStock()) {
            card.stat(Card.ZONE, Tr.t("Stock utilisé"), stats.stockUsed() + " / " + kit.stock())
                    .blank().click(Tr.t("Shift clic"), Tr.t("pour remettre le stock à zéro"));
        }
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.KNOWLEDGE_BOOK), Palette.heading(Tr.t("Statistiques")),
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
        List<String> lore = Card.of(Palette.ERROR_HEX).tag(Tr.t("Suppression")).blank()
                .line(Tr.t("Supprime le kit et l'historique"))
                .line(Tr.t("de récupération des joueurs."))
                .blank()
                .click(Tr.t("Shift clic"), Tr.t("pour supprimer"))
                .build();
        return new GuiItem(KitStyle.decorate(new ItemStack(Material.BARRIER), Palette.ERROR + "<b>Supprimer</b>",
                lore, false), event -> {
            Player player = (Player) event.getWhoClicked();
            if (!event.isShiftClick()) {
                Guis.deny(player);
                return;
            }
            Guis.click(player);
            ConfirmMenu.create(Tr.t("Supprimer un kit"))
                    .subject(KitStyle.icon(kit))
                    .question(Tr.t("Supprimer ") + kit.id() + " ?")
                    .confirmLabel(Tr.t("Supprimer"))
                    .details(Card.of(Palette.ERROR_HEX).blank().deny(Tr.t("Action irréversible")).build())
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
            lines.add(Palette.TEXT + Tr.t("Argent : ") + Numbers.money(rewards.money()));
        }
        if (rewards.shards() > 0L) {
            lines.add(Palette.TEXT + KitPoints.label() + " : " + rewards.shards());
        }
        if (rewards.levels() > 0) {
            lines.add(Palette.TEXT + Tr.t("Niveaux : ") + rewards.levels());
        }
        if (!rewards.keys().isEmpty()) {
            lines.add(Palette.TEXT + Tr.t("Clés : ") + rewards.keys().size() + Tr.t(" caisse(s)"));
        }
        lines.add(Palette.TEXT + Tr.t("Commandes : ") + rewards.commands().size() + Tr.t(", effets : ") + rewards.effects().size());
        return lines;
    }

    private static List<String> conditionSummary(Kit kit) {
        KitRequirements requirements = kit.requirements();
        if (requirements.none()) {
            return List.of(Palette.MUTED + Tr.t("Aucune"));
        }
        List<String> lines = new ArrayList<>();
        if (requirements.playtime() > 0L) {
            lines.add(Palette.TEXT + Tr.t("Temps de jeu : ") + Numbers.duration(requirements.playtime()));
        }
        if (!requirements.kits().isEmpty()) {
            lines.add(Palette.TEXT + Tr.t("Kits requis : ") + String.join(", ", requirements.kits()));
        }
        if (!requirements.worlds().isEmpty()) {
            lines.add(Palette.TEXT + Tr.t("Mondes : ") + String.join(", ", requirements.worlds()));
        }
        if (!requirements.schedule().always()) {
            lines.add(Palette.TEXT + Tr.t("Période limitée"));
        }
        return lines;
    }

    private static List<String> costSummary(KitCost cost) {
        if (cost.free()) {
            return List.of(Palette.MUTED + Tr.t("Gratuit"));
        }
        List<String> lines = new ArrayList<>();
        if (cost.money() > 0.0D) {
            lines.add(Palette.TEXT + Tr.t("Argent : ") + Numbers.money(cost.money()));
        }
        if (cost.shards() > 0L) {
            lines.add(Palette.TEXT + KitPoints.label() + " : " + cost.shards());
        }
        if (cost.levels() > 0) {
            lines.add(Palette.TEXT + Tr.t("Niveaux : ") + cost.levels());
        }
        return lines;
    }

    private static List<String> limitSummary(Kit kit) {
        List<String> lines = new ArrayList<>();
        lines.add(Palette.TEXT + Tr.t("Recharge : ") + (kit.cooldown() <= 0L ? Tr.t("aucune") : Numbers.duration(kit.cooldown())));
        lines.add(Palette.TEXT + Tr.t("Remise à zéro : ") + KitStyle.resetLabel(kit));
        lines.add(Palette.TEXT + Tr.t("Utilisations : ") + (kit.limitedUses() ? kit.maxUses() : Tr.t("illimitées")));
        lines.add(Palette.TEXT + Tr.t("Stock : ") + (kit.limitedStock() ? kit.stock() : Tr.t("illimité")));
        return lines;
    }

    private static List<String> optionSummary(Kit kit) {
        List<String> lines = new ArrayList<>();
        for (KitOptions.Flag flag : KitOptions.Flag.values()) {
            if (kit.options().enabled(flag)) {
                lines.add(Palette.SUCCESS + Palette.CHECK + " " + Palette.TEXT + flag.label());
            }
        }
        return lines.isEmpty() ? List.of(Palette.MUTED + Tr.t("Aucune")) : lines;
    }

    private void options(Player player, String id, Runnable back) {
        Optional<Kit> found = current(player, id, back);
        if (found.isEmpty()) {
            return;
        }
        Kit kit = found.get();
        Runnable reopen = () -> options(player, id, back);
        Gui gui = Gui.builder().rows(5).title(Mini.parse(Palette.smallTitle(Tr.t("Options du kit")))).create();
        Guis.fill(gui);
        KitOptions.Flag[] flags = KitOptions.Flag.values();
        boolean hidden = kit.hideLocked();
        gui.setItem(4, 9, new GuiItem(KitStyle.decorate(new ItemStack(hidden ? Material.ENDER_EYE : Material.ENDER_PEARL),
                (hidden ? Palette.SUCCESS : Palette.MUTED) + Tr.t("<b>Masqué si verrouillé</b>"), Card.of(KitStyle.HEX)
                        .tag(Tr.t("Option")).blank()
                        .line(Tr.t("Les joueurs sans accès ne voient"))
                        .line(Tr.t("pas du tout ce kit."))
                        .blank()
                        .option(hidden, Tr.t("Activée"))
                        .option(!hidden, Tr.t("Désactivée"))
                        .blank()
                        .click(hidden ? Tr.t("pour désactiver") : Tr.t("pour activer"))
                        .build(), hidden), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            editor.setHideLocked(id, !hidden);
            reopen.run();
        }));
        for (int index = 0; index < flags.length; index++) {
            KitOptions.Flag flag = flags[index];
            boolean enabled = kit.options().enabled(flag);
            List<String> lore = Card.of(KitStyle.HEX).tag(Tr.t("Option")).blank()
                    .line(flag.description())
                    .blank()
                    .option(enabled, Tr.t("Activée"))
                    .option(!enabled, Tr.t("Désactivée"))
                    .blank()
                    .click(enabled ? Tr.t("pour désactiver") : Tr.t("pour activer"))
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
        Card card = Card.of(KitStyle.HEX).tag(Tr.t("Cérémonie d'ouverture")).blank()
                .stat(Card.STAR, Tr.t("Réglage"), automatic ? "automatique" : Tr.t("fixé au niveau ") + kit.animationLevel())
                .stat(Card.CHANCE, automatic ? Tr.t("Niveau sans maîtrise") : Tr.t("Niveau"), current.level() + " · "
                        + current.name())
                .blank();
        if (automatic) {
            card.line(Tr.t("Le niveau monte avec la richesse du"))
                    .line(Tr.t("kit et le palier de maîtrise du joueur."));
        } else {
            card.line(Tr.t("Tous les joueurs voient la même"))
                    .line(Tr.t("cérémonie, la maîtrise ne change rien."));
        }
        card.blank();
        for (int level = 0; level <= KitShow.MAXIMUM; level++) {
            card.option(!automatic && kit.animationLevel() == level, Tr.t("Niveau ") + level + " · "
                    + KitShow.of(level).name());
        }
        card.option(automatic, Tr.t("Automatique"));
        card.blank()
                .click(Tr.t("Clic gauche"), Tr.t("niveau suivant"))
                .click(Tr.t("Clic droit"), Tr.t("niveau précédent"))
                .click(Tr.t("Shift clic"), Tr.t("revenir en automatique"));
        return new GuiItem(KitStyle.decorate(new ItemStack(automatic ? Material.FIREWORK_ROCKET
                : Material.FIREWORK_STAR), Palette.heading(Tr.t("Cérémonie d'ouverture")), card.build(), !automatic),
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
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle(Tr.t("Recharge et limites")))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, cleared(Material.CLOCK, Tr.t("Recharge"), kit.cooldown() <= 0L ? Tr.t("aucune")
                        : Numbers.duration(kit.cooldown()), "Exemples : 30m, 12h, 1j 6h",
                viewer -> KitPrompts.duration(viewer, Tr.t("Durée ex 1j 2h"), millis -> editor.setCooldown(id, millis),
                        reopen), viewer -> {
                    editor.setCooldown(id, 0L);
                    reopen.run();
                }));
        gui.setItem(2, 4, new GuiItem(KitStyle.decorate(new ItemStack(Material.DAYLIGHT_DETECTOR),
                Palette.heading(Tr.t("Remise à zéro")), Card.of(KitStyle.HEX).tag(Tr.t("Calendrier")).blank()
                        .stat(Card.TIME, Tr.t("Actuelle"), KitStyle.resetLabel(kit))
                        .blank()
                        .line(Tr.t("Le kit revient à heure fixe, même"))
                        .line(Tr.t("sans attendre la recharge complète."))
                        .blank()
                        .click(Tr.t("Clic gauche"), Tr.t("changer de fréquence"))
                        .click(Tr.t("Clic droit"), Tr.t("choisir l'heure"))
                        .click(Tr.t("Shift clic"), Tr.t("choisir le jour"))
                        .build(), kit.reset() != KitReset.NONE), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            if (event.isShiftClick()) {
                DayOfWeek next = kit.resetDay().plus(1);
                editor.setResetDay(id, next);
                reopen.run();
            } else if (event.isRightClick()) {
                ChatPrompts.open(viewer, Tr.t("l'heure, par exemple 18:00"), typed -> {
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
        gui.setItem(2, 6, cleared(Material.REPEATER, Tr.t("Utilisations maximum"), kit.limitedUses()
                        ? String.valueOf(kit.maxUses()) : Tr.t("illimitées"), Tr.t("1 pour un kit unique par joueur"),
                viewer -> KitPrompts.integer(viewer, Tr.t("Nombre max"), 1, 1_000_000, value -> editor.setMaxUses(id, value),
                        reopen), viewer -> {
                    editor.setMaxUses(id, 0);
                    reopen.run();
                }));
        gui.setItem(2, 8, cleared(Material.BARREL, Tr.t("Stock serveur"), kit.limitedStock() ? String.valueOf(kit.stock())
                        : Tr.t("illimité"), Tr.t("Nombre total pour tout le serveur"),
                viewer -> KitPrompts.integer(viewer, Tr.t("Stock total"), 1, 1_000_000, value -> editor.setStock(id, value),
                        reopen), viewer -> {
                    editor.setStock(id, 0);
                    reopen.run();
                }));
        gui.setItem(3, 3, cleared(Material.WITHER_ROSE, Tr.t("Objets temporaires"), kit.lifetime() <= 0L ? "permanents"
                        : Tr.t("disparaissent après ") + Numbers.duration(kit.lifetime()), Tr.t("Les objets du kit s'effacent seuls"),
                viewer -> KitPrompts.duration(viewer, Tr.t("Durée ex 2h"), millis -> editor.setLifetime(id, millis), reopen),
                viewer -> {
                    editor.setLifetime(id, 0L);
                    reopen.run();
                }));
        gui.setItem(3, 7, action(Material.NETHER_STAR, Tr.t("Maîtrise"), List.of(Palette.TEXT
                        + (kit.mastery().enabled() ? kit.mastery().tiers().size() + Tr.t(" palier(s) propres")
                        : kit.options().mastery() ? Tr.t("modèle par défaut") : Tr.t("désactivée"))), Tr.t("pour éditer les paliers"),
                viewer -> {
                    if (progression != null) {
                        progression.tiers(viewer, id, reopen);
                    }
                }));
        gui.setItem(3, 5, action(Material.EXPERIENCE_BOTTLE, Tr.t("Réductions de recharge"), kit.reductions().isEmpty()
                ? List.of(Palette.MUTED + Tr.t("Aucune")) : kit.reductions().entrySet().stream()
                .map(entry -> Palette.TEXT + entry.getKey() + " : -" + entry.getValue() + "%").toList(),
                Tr.t("pour modifier"), viewer -> KitListMenu.open(viewer, new KitListMenu.Spec(Tr.t("Réductions"),
                        List.of(Tr.t("Une permission réduit la recharge"), Tr.t("d'un pourcentage, ex loadout.kit.vip:25")),
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
        return parseReductions(List.of(line.replace(" ", ""))).isEmpty() ? Tr.t("invalide") : null;
    }

    private static GuiItem cleared(Material material, String title, String value, String hint,
                                   Consumer<Player> edit, Consumer<Player> clear) {
        List<String> lore = Card.of(KitStyle.HEX).tag(title).blank()
                .stat(Card.FLAG, Tr.t("Actuel"), value)
                .line(Palette.MUTED + hint)
                .blank()
                .click(Tr.t("Clic gauche"), Tr.t("modifier"))
                .click(Tr.t("Shift clic"), Tr.t("retirer"))
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
        Gui gui = Gui.builder().rows(3).title(Mini.parse(Palette.smallTitle(Tr.t("Prix du kit")))).create();
        Guis.fill(gui);
        gui.setItem(2, 3, cleared(Material.GOLD_INGOT, Tr.t("Argent"), Numbers.money(kit.cost().money()),
                Tr.t("Retiré du solde à chaque récupération"),
                viewer -> KitPrompts.amount(viewer, Tr.t("Montant"), value -> editor.setCostMoney(id, value), reopen),
                viewer -> {
                    editor.setCostMoney(id, 0.0D);
                    reopen.run();
                }));
        gui.setItem(2, 5, cleared(Material.AMETHYST_SHARD, KitPoints.label(), Numbers.count(kit.cost().shards()),
                Tr.t("Retirés à chaque récupération"),
                viewer -> KitPrompts.integer(viewer, KitPoints.label(), 1, Integer.MAX_VALUE,
                        value -> editor.setCostShards(id, value), reopen),
                viewer -> {
                    editor.setCostShards(id, 0L);
                    reopen.run();
                }));
        gui.setItem(2, 7, cleared(Material.EXPERIENCE_BOTTLE, Tr.t("Niveaux"), String.valueOf(kit.cost().levels()),
                Tr.t("Niveaux d'expérience retirés"),
                viewer -> KitPrompts.integer(viewer, Tr.t("Niveaux"), 1, 10_000, value -> editor.setCostLevels(id, value),
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
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle(Tr.t("Récompenses du kit")))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, cleared(Material.GOLD_BLOCK, Tr.t("Argent"), Numbers.money(rewards.money()), Tr.t("Versé sur le solde"),
                viewer -> KitPrompts.amount(viewer, Tr.t("Montant"), value -> editor.setRewardMoney(id, value), reopen),
                viewer -> {
                    editor.setRewardMoney(id, 0.0D);
                    reopen.run();
                }));
        gui.setItem(2, 3, cleared(Material.AMETHYST_CLUSTER, KitPoints.label(), Numbers.count(rewards.shards()),
                Tr.t("Offerts à chaque récupération"),
                viewer -> KitPrompts.integer(viewer, KitPoints.label(), 1, Integer.MAX_VALUE,
                        value -> editor.setRewardShards(id, value), reopen),
                viewer -> {
                    editor.setRewardShards(id, 0L);
                    reopen.run();
                }));
        gui.setItem(2, 4, cleared(Material.EXPERIENCE_BOTTLE, Tr.t("Niveaux"), String.valueOf(rewards.levels()),
                Tr.t("Niveaux d'expérience offerts"),
                viewer -> KitPrompts.integer(viewer, Tr.t("Niveaux"), 1, 10_000, value -> editor.setRewardLevels(id, value),
                        reopen),
                viewer -> {
                    editor.setRewardLevels(id, 0);
                    reopen.run();
                }));
        gui.setItem(2, 5, action(Material.TRIPWIRE_HOOK, Tr.t("Clés de caisse"), rewards.keys().isEmpty()
                ? List.of(Palette.MUTED + Tr.t("Aucune")) : rewards.keys().entrySet().stream()
                .map(entry -> Palette.TEXT + entry.getValue() + "x " + actions.crateName(entry.getKey())).toList(),
                Tr.t("pour choisir"), viewer -> keys(viewer, id, reopen)));
        gui.setItem(2, 6, action(Material.COMMAND_BLOCK, Tr.t("Commandes"), preview(rewards.commands().stream()
                        .map(KitDispatch::write).toList()), Tr.t("pour modifier"),
                viewer -> KitListMenu.open(viewer, new KitListMenu.Spec(Tr.t("Commandes"), List.of(
                        Tr.t("console: commande lancée par la console"), Tr.t("joueur: commande lancée par le joueur"),
                        Tr.t("Variables : <player>, <uuid>, <kit>")), Tr.t("console: say <player>"), true,
                        () -> service.kit(id).map(value -> value.rewards().commands().stream()
                                .map(KitDispatch::write).toList()).orElse(List.of()),
                        lines -> editor.setRewardList(id, "commands", lines), null, Material.COMMAND_BLOCK), reopen)));
        gui.setItem(2, 7, action(Material.POTION, Tr.t("Effets"), preview(rewards.effects().stream()
                        .map(KitEffect::label).toList()), Tr.t("pour modifier"),
                viewer -> KitListMenu.open(viewer, new KitListMenu.Spec(Tr.t("Effets"), List.of(
                        Tr.t("effet:durée:niveau"), Tr.t("ex speed:2m:2 ou strength:30s:1")), "speed:1m:2", false,
                        () -> service.kit(id).map(value -> value.rewards().effects().stream()
                                .map(KitEffect::write).toList()).orElse(List.of()),
                        lines -> editor.setRewardList(id, "effects", lines),
                        line -> KitEffect.parse(line) == null ? Tr.t("invalide") : null, Material.POTION), reopen)));
        gui.setItem(2, 8, action(Material.OAK_SIGN, Tr.t("Messages"), preview(rewards.messages()), Tr.t("pour modifier"),
                viewer -> KitListMenu.open(viewer, new KitListMenu.Spec(Tr.t("Messages"), List.of(
                        Tr.t("Envoyés au joueur à la récupération."), Tr.t("Variables : <player>, <kit>")),
                        Tr.t("Message"), true, () -> service.kit(id).map(value -> value.rewards().messages())
                        .orElse(List.of()), lines -> editor.setRewardList(id, "messages", lines), null,
                        Material.OAK_SIGN), reopen)));
        gui.setItem(3, 5, action(Material.FILLED_MAP, Tr.t("Lignes bonus"), preview(rewards.lines()), Tr.t("pour modifier"),
                viewer -> KitListMenu.open(viewer, new KitListMenu.Spec(Tr.t("Lignes bonus"), List.of(
                        Tr.t("Décrivent aux joueurs ce que font"), Tr.t("les commandes, ex Grade VIP 7 jours.")),
                        Tr.t("Ligne bonus"), true, () -> service.kit(id).map(value -> value.rewards().lines())
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
        Gui gui = Gui.builder().rows(6).title(Mini.parse(Palette.smallTitle(Tr.t("Clés offertes")))).create();
        Guis.fill(gui);
        List<CrateBridge.KeyCrate> list = crates.crates();
        for (int index = 0; index < Math.min(45, list.size()); index++) {
            CrateBridge.KeyCrate crate = list.get(index);
            int amount = kit.rewards().keys().getOrDefault(crate.id(), 0);
            List<String> lore = Card.of(KitStyle.HEX).tag(Tr.t("Caisse")).blank()
                    .count(Card.AMOUNT, Tr.t("Clés offertes"), amount)
                    .blank()
                    .click(Tr.t("Clic gauche"), "+1")
                    .click(Tr.t("Clic droit"), "-1")
                    .click(Tr.t("Shift clic"), Tr.t("écrire un nombre"))
                    .build();
            gui.setItem(index, new GuiItem(KitStyle.decorate(new ItemStack(Material.TRIPWIRE_HOOK),
                    crate.displayName(), lore, amount > 0), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                ClickType click = event.getClick();
                if (click.isShiftClick()) {
                    KitPrompts.integer(viewer, Tr.t("Nombre de clés"), 0, 10_000,
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
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle(Tr.t("Conditions du kit")))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, cleared(Material.CLOCK, Tr.t("Temps de jeu"), requirements.playtime() <= 0L ? Tr.t("aucun")
                        : Numbers.duration(requirements.playtime()), Tr.t("Temps total passé sur le serveur"),
                viewer -> KitPrompts.duration(viewer, Tr.t("Durée ex 5h"), millis -> editor.setPlaytime(id, millis), reopen),
                viewer -> {
                    editor.setPlaytime(id, 0L);
                    reopen.run();
                }));
        gui.setItem(2, 3, action(Material.CHEST_MINECART, Tr.t("Kits requis"), requirements.kits().isEmpty()
                        ? List.of(Palette.MUTED + Tr.t("Aucun")) : requirements.kits().stream().map(value -> Palette.TEXT + value)
                        .toList(), Tr.t("pour choisir"), viewer -> requiredKits(viewer, id, reopen)));
        gui.setItem(2, 4, action(Material.GRASS_BLOCK, Tr.t("Mondes"), requirements.worlds().isEmpty()
                        ? List.of(Palette.MUTED + Tr.t("Tous")) : requirements.worlds().stream().map(value -> Palette.TEXT + value)
                        .toList(), Tr.t("pour choisir"), viewer -> worlds(viewer, id, reopen)));
        gui.setItem(2, 5, cleared(Material.GOLD_NUGGET, Tr.t("Solde minimum"), Numbers.money(requirements.balance()),
                Tr.t("Le joueur doit posséder ce montant"),
                viewer -> KitPrompts.amount(viewer, Tr.t("Montant"), value -> editor.setMinimumBalance(id, value), reopen),
                viewer -> {
                    editor.setMinimumBalance(id, 0.0D);
                    reopen.run();
                }));
        gui.setItem(2, 6, cleared(Material.ENCHANTING_TABLE, Tr.t("Niveau minimum"), String.valueOf(requirements.level()),
                Tr.t("Niveau d'expérience à atteindre"),
                viewer -> KitPrompts.integer(viewer, Tr.t("Niveau"), 1, 10_000, value -> editor.setMinimumLevel(id, value),
                        reopen),
                viewer -> {
                    editor.setMinimumLevel(id, 0);
                    reopen.run();
                }));
        gui.setItem(2, 7, action(Material.DIAMOND_SWORD, Tr.t("Défis"), requirements.statistics().isEmpty()
                        ? List.of(Palette.MUTED + Tr.t("Aucun")) : requirements.statistics().stream()
                        .map(value -> Palette.TEXT + value.label() + " : " + Numbers.count(value.amount())).toList(),
                Tr.t("pour modifier"), viewer -> KitListMenu.open(viewer, new KitListMenu.Spec(Tr.t("Défis"), List.of(
                        Tr.t("statistique:nombre ou statistique:cible:nombre"),
                        Tr.t("ex mob_kills:100, mine_block:diamond_ore:50,"), Tr.t("kill_entity:zombie:30, fish_caught:20")),
                        "mob_kills:100", true, () -> service.kit(id).map(value -> value.requirements().statistics()
                        .stream().map(KitStatistic::write).toList()).orElse(List.of()),
                        lines -> editor.setStatistics(id, lines),
                        line -> KitStatistic.parse(line) == null ? Tr.t("invalide") : null, Material.DIAMOND_SWORD), reopen)));
        gui.setItem(3, 3, cleared(Material.LIME_BANNER, Tr.t("Ouverture"), schedule.from() <= 0L ? Tr.t("immédiate")
                        : KitSchedule.writeDate(schedule.from(), zone), Tr.t("Format jj/mm/aaaa hh:mm"),
                viewer -> ChatPrompts.open(viewer, Tr.t("la date au format jj/mm/aaaa hh:mm"), typed -> {
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
        gui.setItem(3, 4, cleared(Material.RED_BANNER, Tr.t("Fermeture"), schedule.until() <= 0L ? Tr.t("jamais")
                        : KitSchedule.writeDate(schedule.until(), zone), Tr.t("Format jj/mm/aaaa hh:mm"),
                viewer -> ChatPrompts.open(viewer, Tr.t("la date au format jj/mm/aaaa hh:mm"), typed -> {
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
        gui.setItem(3, 6, action(Material.PAINTING, Tr.t("Jours"), schedule.days().isEmpty() ? List.of(Palette.MUTED
                + Tr.t("Tous les jours")) : List.of(Palette.TEXT + KitSchedule.dayList(schedule.days())), Tr.t("pour choisir"),
                viewer -> days(viewer, id, reopen)));
        gui.setItem(3, 7, cleared(Material.SUNFLOWER, Tr.t("Plage horaire"), schedule.opens() == null ? Tr.t("toute la journée")
                        : schedule.writeHours(), "Format 18:00-23:00",
                viewer -> ChatPrompts.open(viewer, Tr.t("la plage horaire, par exemple 18:00-23:00"), typed -> {
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
        Gui gui = Gui.builder().rows(6).title(Mini.parse(Palette.smallTitle(Tr.t("Kits requis")))).create();
        Guis.fill(gui);
        int index = 0;
        for (Kit other : service.catalog().kits().values()) {
            if (other.id().equals(id) || index >= 45) {
                continue;
            }
            boolean selected = kit.requirements().kits().contains(other.id());
            gui.setItem(index++, new GuiItem(KitStyle.decorate(KitStyle.icon(other), other.name(),
                    Card.of(KitStyle.HEX).blank().option(selected, Tr.t("Requis")).blank()
                            .click(selected ? Tr.t("pour ne plus l'exiger") : Tr.t("pour l'exiger")).build(), selected), event -> {
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
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle(Tr.t("Mondes autorisés")))).create();
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
                    Card.of(KitStyle.HEX).blank().option(selected, Tr.t("Autorisé")).blank()
                            .line(Tr.t("Sans aucun monde coché, le kit"))
                            .line(Tr.t("se récupère partout."))
                            .blank().click(Tr.t("pour basculer")).build(), selected), event -> {
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
        Gui gui = Gui.builder().rows(3).title(Mini.parse(Palette.smallTitle(Tr.t("Jours d'ouverture")))).create();
        Guis.fill(gui);
        Set<DayOfWeek> current = kit.requirements().schedule().days();
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean selected = current.contains(day);
            String name = KitSchedule.dayName(day);
            gui.setItem(2, 1 + day.getValue(), new GuiItem(KitStyle.decorate(new ItemStack(selected
                            ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE),
                    (selected ? Palette.SUCCESS : Palette.MUTED) + "<b>" + Character.toUpperCase(name.charAt(0))
                            + name.substring(1) + "</b>",
                    Card.of(KitStyle.HEX).blank().option(selected, Tr.t("Ouvert")).blank().click(Tr.t("pour basculer")).build(),
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
        Gui gui = Gui.builder().rows(6).title(Mini.parse(Palette.smallTitle(Tr.t("Tirage aléatoire")))).create();
        Guis.fill(gui);
        List<KitPool.Entry> entries = pool.entries();
        for (int index = 0; index < Math.min(45, entries.size()); index++) {
            gui.setItem(index, poolEntry(id, pool, entries.get(index), reopen));
        }
        gui.setItem(6, 2, action(Material.LIME_DYE, Tr.t("Ajouter l'objet en main"), List.of(
                        Palette.TEXT + Tr.t("Poids 10, quantité de l'objet tenu.")), Tr.t("pour ajouter"),
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
        gui.setItem(6, 3, action(Material.SHULKER_BOX, Tr.t("Importer une shulker"), List.of(
                        Palette.TEXT + Tr.t("Chaque objet de la boîte tenue"), Palette.TEXT + Tr.t("devient une entrée.")),
                Tr.t("pour importer"), viewer -> {
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
        gui.setItem(6, 5, cleared(Material.ENDER_EYE, Tr.t("Tirages"), String.valueOf(pool.rolls()),
                Tr.t("Objets tirés à chaque récupération"),
                viewer -> KitPrompts.integer(viewer, Tr.t("Tirages"), 0, KitPool.MAXIMUM_ROLLS,
                        value -> editor.setPoolRolls(id, value), reopen),
                viewer -> {
                    editor.setPoolRolls(id, 0);
                    reopen.run();
                }));
        gui.setItem(6, 7, new GuiItem(KitStyle.decorate(new ItemStack(pool.unique() ? Material.AMETHYST_SHARD
                        : Material.QUARTZ), Palette.heading(Tr.t("Doublons")), Card.of(KitStyle.HEX).blank()
                        .option(pool.unique(), Tr.t("Chaque objet sort une seule fois"))
                        .option(!pool.unique(), Tr.t("Un objet peut sortir plusieurs fois"))
                        .blank().click(Tr.t("pour basculer")).build(), pool.unique()), event -> {
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
                : entry.minimum() + Tr.t(" à ") + entry.maximum();
        Card card = Card.of(KitStyle.HEX).tag(Tr.t("Entrée ") + entry.id()).blank()
                .count(Card.CHANCE, Tr.t("Poids"), entry.weight())
                .stat(Card.CHANCE, pool.exact() ? Tr.t("Chance") : Tr.t("Part"), KitPreviewMenu.percent(pool.exact()
                        ? pool.chance(entry) : pool.share(entry)))
                .stat(Card.AMOUNT, Tr.t("Quantité"), amount)
                .blank()
                .click(Tr.t("Clic gauche"), Tr.t("changer le poids"))
                .click(Tr.t("Clic droit"), Tr.t("changer la quantité, ex 2-5"))
                .click(Tr.t("Shift clic droit"), Tr.t("retirer"));
        return new GuiItem(KitStyle.decorate(base, Palette.heading(entry.id()), card.build(), false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            ClickType click = event.getClick();
            if (click == ClickType.SHIFT_RIGHT) {
                editor.removePoolEntry(id, entry.id());
                reopen.run();
            } else if (click.isRightClick()) {
                ChatPrompts.open(viewer, Tr.t("la plage min-max, par exemple 2-5"), typed -> {
                    int[] range = range(typed);
                    if (range == null) {
                        KitPrompts.invalid(viewer, typed);
                    } else {
                        editor.setPoolAmounts(id, entry.id(), range[0], range[1]);
                    }
                    reopen.run();
                });
            } else {
                KitPrompts.integer(viewer, Tr.t("Poids"), 1, 1_000_000, value -> editor.setPoolWeight(id, entry.id(), value),
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
