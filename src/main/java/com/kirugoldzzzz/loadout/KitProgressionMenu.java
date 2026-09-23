package com.kirugoldzzzz.loadout;

import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.gui.ChatPrompts;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KitProgressionMenu {

    static final int ROWS = 6;
    static final String STREAKS = "progression.streaks";
    static final String STREAK_MILESTONES = "progression.streaks.milestones";
    static final String COLLECTION = "progression.collection";
    static final String FEATURED = "progression.featured";

    private final KitService service;
    private final KitEditor editor;

    public KitProgressionMenu(KitService service, KitEditor editor) {
        this.service = service;
        this.editor = editor;
    }

    public void open(Player player, Runnable back) {
        Runnable reopen = () -> open(player, back);
        KitProgression progression = service.catalog().progression();
        Gui gui = Gui.builder().rows(3).title(Mini.parse(Palette.smallTitle("Progression des kits"))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, KitEditorMenu.action(Material.EXPERIENCE_BOTTLE, "Maîtrise par défaut", List.of(
                        Palette.TEXT + progression.mastery().tiers().size() + " palier(s)",
                        Palette.MUTED + "S'applique à tous les kits répétables", Palette.MUTED + "sans maîtrise propre."),
                "pour éditer", viewer -> tiers(viewer, null, reopen)));
        gui.setItem(2, 4, KitEditorMenu.action(Material.BLAZE_POWDER, "Séries", List.of(
                        Palette.TEXT + (progression.streaks().enabled() ? "Activées" : "Désactivées"),
                        Palette.TEXT + "+" + progression.streaks().bonusPerClaim() + "% par récupération, max "
                                + progression.streaks().maximumBonus() + "%"),
                "pour configurer", viewer -> streaks(viewer, reopen)));
        gui.setItem(2, 6, KitEditorMenu.action(Material.KNOWLEDGE_BOOK, "Paliers de collection", List.of(
                        Palette.TEXT + progression.collection().size() + " palier(s)"), "pour éditer",
                viewer -> milestones(viewer, COLLECTION, "Paliers de collection", "Nombre de kits", reopen)));
        gui.setItem(2, 8, KitEditorMenu.action(Material.CLOCK, "Kit du jour", List.of(
                        Palette.TEXT + (progression.featured().enabled() ? "Activé" : "Désactivé"),
                        Palette.TEXT + "-" + progression.featured().discount() + "% sur un kit payant"),
                "pour configurer", viewer -> featured(viewer, reopen)));
        gui.setItem(3, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(3, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    public void tiers(Player player, String kit, Runnable back) {
        Runnable reopen = () -> tiers(player, kit, back);
        String root = KitEditor.masteryRoot(kit);
        KitMastery mastery = kit == null ? service.catalog().progression().mastery()
                : service.kit(kit).map(Kit::mastery).orElse(KitMastery.NONE);
        ConfigurationSection section = editor.section(root);
        Gui gui = Gui.builder().rows(4).title(Mini.parse(Palette.smallTitle(kit == null ? "Maîtrise par défaut"
                : "Maîtrise de " + kit))).create();
        Guis.fill(gui);
        List<String> keys = section == null ? List.of() : new ArrayList<>(section.getKeys(false));
        keys.sort((left, right) -> Integer.compare(section.getInt(left + ".claims"), section.getInt(right + ".claims")));
        for (int index = 0; index < Math.min(7, keys.size()); index++) {
            String key = keys.get(index);
            KitMastery.Tier tier = index < mastery.tiers().size() ? mastery.tiers().get(index) : null;
            gui.setItem(2, 2 + index, tierItem(player, root, key, tier, index + 1, reopen));
        }
        gui.setItem(4, 3, KitEditorMenu.action(Material.LIME_DYE, "Ajouter un palier", List.of(
                        Palette.TEXT + "Écrivez nom et récupérations,", Palette.TEXT + "ex Bronze 5"), "pour ajouter",
                viewer -> ChatPrompts.open(viewer, "le nom et le nombre de récupérations", typed -> {
                    String[] parts = typed == null ? new String[0] : typed.trim().split("\\s+");
                    int claims = parts.length >= 2 ? Numbers.parseInt(parts[parts.length - 1], -1) : -1;
                    if (claims <= 0) {
                        KitPrompts.invalid(viewer, typed == null ? "" : typed);
                    } else {
                        String name = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
                        editor.addTier(root, name, claims);
                    }
                    reopen.run();
                })));
        if (kit != null) {
            gui.setItem(4, 5, KitEditorMenu.action(Material.PAPER, "Copier le modèle", List.of(
                            Palette.TEXT + "Remplace les paliers de ce kit", Palette.TEXT + "par ceux par défaut."),
                    "pour copier", viewer -> {
                        editor.copyTemplate(kit);
                        reopen.run();
                    }));
            gui.setItem(4, 7, KitEditorMenu.action(Material.BARRIER, "Utiliser le modèle", List.of(
                            Palette.TEXT + "Supprime les paliers propres", Palette.TEXT + "au kit."),
                    "pour revenir au modèle", viewer -> {
                        editor.clearMastery(kit);
                        reopen.run();
                    }));
        }
        gui.setItem(4, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(4, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private GuiItem tierItem(Player player, String root, String key, KitMastery.Tier tier, int level, Runnable reopen) {
        Card card = Card.of(KitStyle.HEX).tag("Palier " + level).blank();
        if (tier != null) {
            card.count(Card.AMOUNT, "Récupérations", tier.claims())
                    .stat(Card.TIME, "Recharge", "-" + tier.cooldownReduction() + "%")
                    .stat(Card.MONEY, "Bonus", "+" + tier.bonus() + "%")
                    .stat(Card.CHANCE, "Tirages en plus", tier.extraRolls())
                    .count(Card.AMOUNT, "Objets bonus", tier.items().size())
                    .money("Prix d'achat", tier.upgrade().money());
        }
        card.blank()
                .click("Clic gauche", "récupérations requises")
                .click("Clic droit", "réduction de recharge %")
                .click("Shift clic gauche", "bonus de récompenses %")
                .click("Molette ou Q", "tirages en plus")
                .click("Touche 1", "prix d'achat")
                .click("Touche 2", "ajouter l'objet en main")
                .click("Touche 3", "vider les objets bonus")
                .click("Shift clic droit", "supprimer");
        String name = Palette.heading(tier == null ? key : tier.name());
        Material icon = KitMasteryMenu.TIER_ICONS[Math.min(KitMasteryMenu.TIER_ICONS.length - 1, level - 1)];
        return new GuiItem(KitStyle.decorate(new ItemStack(icon), name, card.build(), false), event -> {
            Player viewer = (Player) event.getWhoClicked();
            Guis.click(viewer);
            ClickType click = event.getClick();
            switch (click) {
                case SHIFT_RIGHT -> {
                    editor.removeTier(root, key);
                    reopen.run();
                }
                case RIGHT -> KitPrompts.integer(viewer, "Réduction %", 0, KitMastery.MAXIMUM_REDUCTION,
                        value -> editor.setTierValue(root, key, "cooldown-reduction", value), reopen);
                case SHIFT_LEFT -> KitPrompts.integer(viewer, "Bonus %", 0, 1_000,
                        value -> editor.setTierValue(root, key, "bonus", value), reopen);
                case MIDDLE, DROP, CONTROL_DROP -> KitPrompts.integer(viewer, "Tirages en plus", 0, KitPool.MAXIMUM_ROLLS,
                        value -> editor.setTierValue(root, key, "extra-rolls", value), reopen);
                case NUMBER_KEY -> {
                    int button = event.getHotbarButton();
                    if (button == 0) {
                        KitPrompts.amount(viewer, "Prix d'achat", value -> editor.setTierValue(root, key, "upgrade.money",
                                value <= 0.0D ? null : Numbers.round(value)), reopen);
                    } else if (button == 1) {
                        ItemStack held = viewer.getInventory().getItemInMainHand();
                        if (held.getType().isAir()) {
                            Guis.deny(viewer);
                            Messages.send(viewer, "kits.hold-item");
                        } else {
                            editor.addTierItem(root, key, held.clone());
                        }
                        reopen.run();
                    } else if (button == 2) {
                        editor.clearTierItems(root, key);
                        reopen.run();
                    } else {
                        reopen.run();
                    }
                }
                default -> KitPrompts.integer(viewer, "Récupérations", 1, 1_000_000,
                        value -> editor.setTierValue(root, key, "claims", value), reopen);
            }
        });
    }

    private void streaks(Player player, Runnable back) {
        Runnable reopen = () -> streaks(player, back);
        KitProgression.Streaks streaks = service.catalog().progression().streaks();
        Gui gui = Gui.builder().rows(3).title(Mini.parse(Palette.smallTitle("Séries"))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, KitEditorMenu.action(streaks.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "Séries " + (streaks.enabled() ? "activées" : "désactivées"), List.of(
                        Palette.TEXT + "Récupérer un kit à chaque remise", Palette.TEXT + "à zéro fait grimper la série."),
                "pour basculer", viewer -> {
                    editor.setPath(STREAKS + ".enabled", !streaks.enabled(), "séries");
                    reopen.run();
                }));
        gui.setItem(2, 4, KitEditorMenu.action(Material.GOLD_NUGGET, "Bonus par récupération", List.of(
                        Palette.TEXT + "+" + streaks.bonusPerClaim() + "% par récupération de la série"),
                "pour modifier", viewer -> KitPrompts.integer(viewer, "Bonus %", 0, 100,
                        value -> editor.setPath(STREAKS + ".bonus-per-claim", value, "bonus de série"), reopen)));
        gui.setItem(2, 5, KitEditorMenu.action(Material.GOLD_BLOCK, "Bonus maximum", List.of(
                        Palette.TEXT + "Plafond : " + streaks.maximumBonus() + "%"),
                "pour modifier", viewer -> KitPrompts.integer(viewer, "Maximum %", 0, 1_000,
                        value -> editor.setPath(STREAKS + ".maximum-bonus", value, "bonus maximum"), reopen)));
        gui.setItem(2, 6, KitEditorMenu.action(Material.BELL, "Alerte de série en danger", List.of(
                        Palette.TEXT + (streaks.warning() <= 0L ? "désactivée" : Numbers.duration(streaks.warning())
                                + " avant la remise à zéro")),
                "pour modifier", viewer -> KitPrompts.duration(viewer, "Durée ex 2h",
                        millis -> editor.setPath(STREAKS + ".warning", millis <= 0L ? null : KitDurations.write(millis),
                                "alerte de série"), reopen)));
        gui.setItem(2, 8, KitEditorMenu.action(Material.FIREWORK_STAR, "Paliers de série", List.of(
                        Palette.TEXT + streaks.milestones().size() + " palier(s)"), "pour éditer",
                viewer -> milestones(viewer, STREAK_MILESTONES, "Paliers de série", "Longueur de série", reopen)));
        gui.setItem(3, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(3, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void milestones(Player player, String root, String title, String prompt, Runnable back) {
        Runnable reopen = () -> milestones(player, root, title, prompt, back);
        Map<Integer, KitRewards> current = root.equals(COLLECTION) ? service.catalog().progression().collection()
                : service.catalog().progression().streaks().milestones();
        Gui gui = Gui.builder().rows(ROWS).title(Mini.parse(Palette.smallTitle(title))).create();
        Guis.fill(gui);
        int index = 0;
        for (Map.Entry<Integer, KitRewards> entry : current.entrySet()) {
            if (index >= 45) {
                break;
            }
            int count = entry.getKey();
            KitRewards rewards = entry.getValue();
            String key = root + "." + count;
            Card card = Card.of(KitStyle.HEX).tag(title).blank()
                    .money("Argent", rewards.money())
                    .count(Card.STAR, "Fragments", rewards.shards())
                    .count(Card.STAR, "Niveaux", rewards.levels())
                    .count(Card.FLAG, "Commandes", rewards.commands().size())
                    .blank()
                    .click("Clic gauche", "argent")
                    .click("Clic droit", "fragments")
                    .click("Shift clic gauche", "niveaux")
                    .click("Touche 1", "commandes")
                    .click("Touche 2", "lignes affichées")
                    .click("Shift clic droit", "supprimer");
            gui.setItem(index++, new GuiItem(KitStyle.decorate(new ItemStack(Material.CANDLE),
                    Palette.heading(String.valueOf(count)), card.build(), false), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                switch (event.getClick()) {
                    case SHIFT_RIGHT -> {
                        editor.setPath(key, null, title + " " + count);
                        reopen.run();
                    }
                    case RIGHT -> KitPrompts.integer(viewer, "Fragments", 0, Integer.MAX_VALUE,
                            value -> editor.setPath(key + ".shards", value <= 0 ? null : value, title), reopen);
                    case SHIFT_LEFT -> KitPrompts.integer(viewer, "Niveaux", 0, 10_000,
                            value -> editor.setPath(key + ".levels", value <= 0 ? null : value, title), reopen);
                    case NUMBER_KEY -> {
                        String list = event.getHotbarButton() == 0 ? "commands" : "lines";
                        KitListMenu.open(viewer, new KitListMenu.Spec(list.equals("commands") ? "Commandes" : "Lignes",
                                List.of("Variables : <player>, <uuid>, <kit>"),
                                list.equals("commands") ? "console: say <player>" : "Ligne", true,
                                () -> {
                                    ConfigurationSection section = editor.section(key);
                                    return section == null ? List.of() : section.getStringList(list);
                                },
                                lines -> editor.setPath(key + "." + list, lines.isEmpty() ? null : new ArrayList<>(lines),
                                        title), null, Material.PAPER), reopen);
                    }
                    default -> KitPrompts.amount(viewer, "Argent", value -> editor.setPath(key + ".money",
                            value <= 0.0D ? 0 : Numbers.round(value), title), reopen);
                }
            }));
        }
        gui.setItem(ROWS, 5, KitEditorMenu.action(Material.LIME_DYE, "Ajouter un palier", List.of(
                        Palette.TEXT + prompt), "pour ajouter",
                viewer -> KitPrompts.integer(viewer, prompt, 1, 100_000, value -> editor.addMilestone(root, value),
                        reopen)));
        gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void featured(Player player, Runnable back) {
        Runnable reopen = () -> featured(player, back);
        KitProgression.Featured featured = service.catalog().progression().featured();
        long now = System.currentTimeMillis();
        String today = service.featured(now);
        Gui gui = Gui.builder().rows(3).title(Mini.parse(Palette.smallTitle("Kit du jour"))).create();
        Guis.fill(gui);
        gui.setItem(2, 2, KitEditorMenu.action(featured.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "Kit du jour " + (featured.enabled() ? "activé" : "désactivé"), List.of(
                        Palette.TEXT + "Aujourd'hui : " + (today == null ? "aucun" : today)),
                "pour basculer", viewer -> {
                    editor.setPath(FEATURED + ".enabled", !featured.enabled(), "kit du jour");
                    reopen.run();
                }));
        gui.setItem(2, 4, KitEditorMenu.action(Material.EMERALD, "Réduction", List.of(
                        Palette.TEXT + "-" + featured.discount() + "%"), "pour modifier",
                viewer -> KitPrompts.integer(viewer, "Réduction %", 0, 90,
                        value -> editor.setPath(FEATURED + ".discount", value, "réduction du kit du jour"), reopen)));
        gui.setItem(2, 6, KitEditorMenu.action(Material.CLOCK, "Heure de rotation", List.of(
                        Palette.TEXT + KitSchedule.TIME.format(featured.rotation())), "pour modifier",
                viewer -> ChatPrompts.open(viewer, "l'heure, par exemple 00:00", typed -> {
                    java.time.LocalTime time = KitSchedule.parseTime(typed);
                    if (time == null) {
                        KitPrompts.invalid(viewer, typed);
                    } else {
                        editor.setPath(FEATURED + ".rotation", KitSchedule.TIME.format(time), "rotation");
                    }
                    reopen.run();
                })));
        gui.setItem(2, 8, KitEditorMenu.action(Material.CHEST, "Kits en rotation", featured.kits().isEmpty()
                        ? List.of(Palette.TEXT + "Tous les kits payants") : featured.kits().stream()
                        .map(id -> Palette.TEXT + id).toList(), "pour choisir",
                viewer -> featuredKits(viewer, reopen)));
        gui.setItem(3, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(3, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    private void featuredKits(Player player, Runnable back) {
        Runnable reopen = () -> featuredKits(player, back);
        List<String> listed = service.catalog().progression().featured().kits();
        Gui gui = Gui.builder().rows(ROWS).title(Mini.parse(Palette.smallTitle("Kits en rotation"))).create();
        Guis.fill(gui);
        int index = 0;
        for (Kit kit : service.catalog().kits().values()) {
            if (index >= 45) {
                break;
            }
            boolean selected = listed.contains(kit.id());
            gui.setItem(index++, new GuiItem(KitStyle.decorate(KitStyle.icon(kit), kit.name(), Card.of(KitStyle.HEX)
                    .blank().option(selected, "En rotation").line(Palette.MUTED + "Sans sélection, tous les kits")
                    .line(Palette.MUTED + "payants tournent.").blank().click("pour basculer").build(), selected), event -> {
                Player viewer = (Player) event.getWhoClicked();
                Guis.click(viewer);
                List<String> updated = new ArrayList<>(listed);
                if (selected) {
                    updated.remove(kit.id());
                } else {
                    updated.add(kit.id());
                }
                editor.setPath(FEATURED + ".kits", updated.isEmpty() ? null : updated, "kits en rotation");
                reopen.run();
            }));
        }
        gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }
}
