package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class KitStyle {

    public static final String HEX = Palette.PRIMARY_HEX;
    private static final int BAR = 12;

    private KitStyle() {
    }

    public static ItemStack icon(Kit kit) {
        ItemStack base = kit.icon() == null ? new ItemStack(Material.CHEST) : kit.icon().clone();
        base.setAmount(1);
        return base;
    }

    public static ItemStack decorate(ItemStack base, String name, List<String> lore, boolean glow) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        meta.displayName(Mini.live(name));
        meta.lore(Mini.live(lore));
        meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null);
        meta.addItemFlags(ItemFlag.values());
        copy.setItemMeta(meta);
        return copy;
    }

    public static String stateLine(KitStatus status, long now) {
        return switch (status.state()) {
            case AVAILABLE -> Card.noteLine(Palette.SUCCESS, Palette.CHECK, "Disponible maintenant");
            case COOLDOWN -> Card.noteLine(Palette.WARNING, Card.TIME, "Prêt dans " + Palette.WARNING
                    + Numbers.duration(status.remaining()));
            case EXHAUSTED -> Card.noteLine(Palette.MUTED, Palette.CHECK, "Déjà récupéré, plus d'utilisation");
            case SOLD_OUT -> Card.noteLine(Palette.ERROR, Palette.CROSS, "Rupture de stock");
            case LOCKED -> Card.noteLine(Palette.ERROR, Palette.CROSS, "Verrouillé");
            case CLOSED -> Card.noteLine(Palette.WARNING, Card.TIME, "Hors période");
            case REQUIREMENTS -> Card.noteLine(Palette.ERROR, Palette.CROSS, "Conditions non remplies");
            case UNAFFORDABLE -> Card.noteLine(Palette.ERROR, Card.MONEY, "Moyens insuffisants");
        };
    }

    public static String progress(double fraction) {
        double clamped = Math.max(0.0D, Math.min(1.0D, fraction));
        int filled = (int) Math.round(clamped * BAR);
        return Palette.WARNING + "▰".repeat(filled) + Palette.MUTED + "▱".repeat(BAR - filled) + " " + Palette.TEXT
                + Math.round(clamped * 100.0D) + "%";
    }

    public record Extras(KitService.Progress progress, int discount, boolean favorite, boolean team) {

        public static final Extras NONE = new Extras(null, 0, false, false);
    }

    public static List<String> card(Kit kit, KitStatus status, KitCatalog catalog, long now, long lastClaim,
                                    Function<String, String> crateNames, boolean clicks) {
        return card(kit, status, catalog, now, lastClaim, crateNames, clicks, Extras.NONE);
    }

    public static List<String> card(Kit kit, KitStatus status, KitCatalog catalog, long now, long lastClaim,
                                    Function<String, String> crateNames, boolean clicks, Extras extras) {
        String tag = kit.category() == null ? "Kit"
                : "Kit · " + catalog.category(kit.category()).map(category -> Mini.plain(Mini.label(category.name())))
                .orElse(kit.category());
        Card card = Card.of(HEX).tag(tag + (extras.favorite() ? " · ★ favori" : "") + (extras.team() ? " · équipe" : ""));
        if (extras.discount() > 0) {
            card.raw(Palette.WARNING + "⚡ <b>" + Card.small("Kit du jour") + "</b> " + Palette.SUCCESS + "-"
                    + extras.discount() + "% " + Palette.TEXT + "aujourd'hui");
        }
        if (!kit.description().isEmpty()) {
            card.blank();
            kit.description().forEach(line -> card.raw(Palette.TEXT + line));
        }
        card.section("Statut");
        card.raw(stateLine(status, now));
        if (status.state() == KitStatus.State.COOLDOWN && lastClaim > 0L && status.readyAt() > lastClaim) {
            long total = status.readyAt() - lastClaim;
            card.raw(progress(1.0D - status.remaining() / (double) total));
        }
        if (status.state() == KitStatus.State.LOCKED && !kit.hint().isEmpty()) {
            kit.hint().forEach(line -> card.raw(Palette.MUTED + line));
        }
        progression(card, extras.progress(), catalog);
        contents(card, kit, crateNames);
        conditions(card, kit, status, catalog.settings().zone());
        limits(card, kit, status, catalog.settings().zone());
        if (clicks) {
            card.blank();
            if (status.available()) {
                card.click("Clic gauche", kit.cost().free() ? "pour récupérer" : "pour acheter et récupérer");
            }
            card.click("Clic droit", "pour voir le contenu");
            if (kit.options().giftable()) {
                card.click("Shift clic gauche", "pour offrir à un joueur");
            }
            card.click("Shift clic droit", extras.favorite() ? "pour retirer des favoris" : "pour ajouter aux favoris");
        }
        return card.build();
    }

    static void progression(Card card, KitService.Progress progress, KitCatalog catalog) {
        if (progress == null) {
            return;
        }
        boolean mastery = progress.mastery().enabled();
        boolean streaks = progress.streak() > 0 || progress.best() > 1;
        if (!mastery && !streaks && progress.bonus() <= 0) {
            return;
        }
        card.section("Progression");
        if (mastery) {
            KitMastery.Tier tier = progress.mastery().tier(progress.level());
            KitMastery.Tier next = progress.mastery().next(progress.level());
            card.stat(Card.STAR, "Maîtrise", tier == null ? "aucun palier" : tier.name() + Palette.MUTED + " (palier "
                    + progress.level() + "/" + progress.mastery().tiers().size() + ")");
            if (next != null) {
                card.raw(progress(progress.mastery().progress(progress.uses(), progress.level())) + Palette.MUTED
                        + " vers " + next.name() + " (" + progress.uses() + "/" + next.claims() + ")");
            } else {
                card.raw(Palette.SUCCESS + "✦ Maîtrise complète");
            }
        }
        if (progress.streak() > 0) {
            Integer milestone = catalog.progression().streaks().nextMilestone(progress.streak());
            card.stat(Palette.WARNING, "✹", "Série", progress.streak() + Palette.MUTED + " (record " + progress.best()
                    + ")" + (milestone == null ? "" : Palette.MUTED + ", palier à " + milestone));
        } else if (progress.best() > 1) {
            card.stat(Palette.MUTED, "✹", "Série", "0" + Palette.MUTED + " (record " + progress.best() + ")");
        }
        if (progress.bonus() > 0) {
            card.stat(Palette.SUCCESS, "✚", "Bonus de récompenses", "+" + progress.bonus() + "%");
        }
        KitShow show = KitShow.of(KitShow.levelFor(progress.kit(), progress.level()));
        card.stat(Palette.WARNING, "✦", "Cérémonie", show.name() + Palette.MUTED + " (niveau " + show.level() + "/"
                + KitShow.MAXIMUM + ")");
    }

    static void contents(Card card, Kit kit, Function<String, String> crateNames) {
        card.section("Contenu");
        int armour = 0;
        for (Integer slot : kit.contents().keySet()) {
            if (KitSlots.armour(slot)) {
                armour++;
            }
        }
        int items = kit.contents().size();
        if (items > 0) {
            card.stat(Card.AMOUNT, "Objets", items + (armour > 0 ? Palette.MUTED + " dont " + armour + " pièce(s) d'armure" : ""));
        }
        if (!kit.pool().empty()) {
            card.stat(Card.CHANCE, "Tirage", kit.pool().effectiveRolls() + " parmi " + kit.pool().entries().size()
                    + " possibilités");
        }
        KitRewards rewards = kit.rewards();
        if (rewards.money() > 0.0D) {
            card.money("Argent", rewards.money());
        }
        if (rewards.shards() > 0L) {
            card.stat(Card.STAR, "Fragments", Numbers.count(rewards.shards()));
        }
        if (rewards.levels() > 0) {
            card.stat(Card.STAR, "Niveaux", "+" + rewards.levels());
        }
        for (Map.Entry<String, Integer> key : rewards.keys().entrySet()) {
            card.stat(Card.FLAG, "Clés", key.getValue() + "x " + crateNames.apply(key.getKey()));
        }
        for (KitEffect effect : rewards.effects()) {
            card.stat(Card.ZONE, "Effet", effect.label() + Palette.MUTED + " " + Numbers.duration(effect.seconds() * 1_000L));
        }
        for (String line : rewards.lines()) {
            card.stat(Card.CALL, "Bonus", line);
        }
        if (kit.empty()) {
            card.line(Palette.MUTED + "Rien pour le moment");
        }
    }

    static void conditions(Card card, Kit kit, KitStatus status, ZoneId zone) {
        List<KitStatus.Check> shown = new ArrayList<>();
        for (KitStatus.Check check : status.checks()) {
            if (check.kind() != KitStatus.Kind.PERMISSION && check.kind() != KitStatus.Kind.MONEY
                    && check.kind() != KitStatus.Kind.SHARDS && check.kind() != KitStatus.Kind.LEVELS) {
                shown.add(check);
            }
        }
        List<String> schedule = kit.requirements().schedule().describe(zone);
        if (shown.isEmpty() && schedule.isEmpty()) {
            return;
        }
        card.section("Conditions");
        for (KitStatus.Check check : shown) {
            card.raw(check(check));
        }
        for (String line : schedule) {
            card.raw(Palette.MUTED + "  " + line);
        }
    }

    static String check(KitStatus.Check check) {
        String tint = check.met() ? Palette.SUCCESS : Palette.ERROR;
        return tint + (check.met() ? Palette.CHECK : Palette.CROSS) + " " + Palette.TEXT + check.label() + " : "
                + tint + check.detail();
    }

    static void limits(Card card, Kit kit, KitStatus status, ZoneId zone) {
        boolean any = !kit.cost().free() || kit.timed() || kit.limitedUses() || kit.limitedStock();
        if (!any) {
            return;
        }
        card.section("Règles");
        for (KitStatus.Check check : status.checks()) {
            if (check.kind() == KitStatus.Kind.MONEY || check.kind() == KitStatus.Kind.SHARDS
                    || check.kind() == KitStatus.Kind.LEVELS) {
                card.raw(check(check));
            }
        }
        if (kit.cooldown() > 0L) {
            long effective = KitRules.cooldown(kit, status.reduction());
            String bonus = status.reduction() > 0 ? Palette.SUCCESS + " (-" + status.reduction() + "% grâce à votre grade)" : "";
            card.stat(Card.TIME, "Recharge", Numbers.duration(effective) + bonus);
        }
        if (kit.reset() != KitReset.NONE) {
            card.stat(Card.TIME, "Remise à zéro", resetLabel(kit));
        }
        if (kit.limitedUses()) {
            card.stat(Card.FLAG, "Utilisations", status.usesLeft() + " / " + kit.maxUses() + " restantes");
        }
        if (kit.limitedStock()) {
            card.stat(Card.ZONE, "Stock serveur", status.stockLeft() + " / " + kit.stock() + " restants");
        }
    }

    public static String resetLabel(Kit kit) {
        String time = KitSchedule.TIME.format(kit.resetTime());
        return switch (kit.reset()) {
            case NONE -> "aucune";
            case DAILY -> "chaque jour à " + time;
            case WEEKLY -> "chaque " + KitSchedule.dayName(kit.resetDay()) + " à " + time;
            case MONTHLY -> "le 1er du mois à " + time;
        };
    }
}
