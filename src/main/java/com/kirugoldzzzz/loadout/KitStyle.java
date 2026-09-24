package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
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
            case AVAILABLE -> Card.noteLine(Palette.SUCCESS, Palette.CHECK, Tr.t("Disponible maintenant"));
            case COOLDOWN -> Card.noteLine(Palette.WARNING, Card.TIME, Tr.t("Prêt dans ") + Palette.WARNING
                    + Numbers.duration(status.remaining()));
            case EXHAUSTED -> Card.noteLine(Palette.MUTED, Palette.CHECK, Tr.t("Déjà récupéré, plus d'utilisation"));
            case SOLD_OUT -> Card.noteLine(Palette.ERROR, Palette.CROSS, Tr.t("Rupture de stock"));
            case LOCKED -> Card.noteLine(Palette.ERROR, Palette.CROSS, Tr.t("Verrouillé"));
            case CLOSED -> Card.noteLine(Palette.WARNING, Card.TIME, Tr.t("Hors période"));
            case REQUIREMENTS -> Card.noteLine(Palette.ERROR, Palette.CROSS, Tr.t("Conditions non remplies"));
            case UNAFFORDABLE -> Card.noteLine(Palette.ERROR, Card.MONEY, Tr.t("Moyens insuffisants"));
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
        String tag = kit.category() == null ? Tr.t("Kit")
                : "Kit · " + catalog.category(kit.category()).map(category -> Mini.plain(Mini.label(category.name())))
                .orElse(kit.category());
        Card card = Card.of(HEX).tag(tag + (extras.favorite() ? " · ★ favori" : "") + (extras.team() ? Tr.t(" · équipe") : ""));
        if (extras.discount() > 0) {
            card.raw(Palette.WARNING + "⚡ <b>" + Card.small(Tr.t("Kit du jour")) + "</b> " + Palette.SUCCESS + "-"
                    + extras.discount() + "% " + Palette.TEXT + Tr.t("aujourd'hui"));
        }
        if (!kit.description().isEmpty()) {
            card.blank();
            kit.description().forEach(line -> card.raw(Palette.TEXT + line));
        }
        card.section(Tr.t("Statut"));
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
                card.click(Tr.t("Clic gauche"), kit.cost().free() ? Tr.t("pour récupérer") : Tr.t("pour acheter et récupérer"));
            }
            card.click(Tr.t("Clic droit"), Tr.t("pour voir le contenu"));
            if (kit.options().giftable()) {
                card.click(Tr.t("Shift clic gauche"), Tr.t("pour offrir à un joueur"));
            }
            card.click(Tr.t("Shift clic droit"), extras.favorite() ? Tr.t("pour retirer des favoris") : Tr.t("pour ajouter aux favoris"));
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
        card.section(Tr.t("Progression"));
        if (mastery) {
            KitMastery.Tier tier = progress.mastery().tier(progress.level());
            KitMastery.Tier next = progress.mastery().next(progress.level());
            card.stat(Card.STAR, Tr.t("Maîtrise"), tier == null ? Tr.t("aucun palier") : tier.name() + Palette.MUTED + " (palier "
                    + progress.level() + "/" + progress.mastery().tiers().size() + ")");
            if (next != null) {
                card.raw(progress(progress.mastery().progress(progress.uses(), progress.level())) + Palette.MUTED
                        + Tr.t(" vers ") + next.name() + " (" + progress.uses() + "/" + next.claims() + ")");
            } else {
                card.raw(Palette.SUCCESS + Tr.t("✦ Maîtrise complète"));
            }
        }
        if (progress.streak() > 0) {
            Integer milestone = catalog.progression().streaks().nextMilestone(progress.streak());
            card.stat(Palette.WARNING, "✹", Tr.t("Série"), progress.streak() + Palette.MUTED + " (record " + progress.best()
                    + ")" + (milestone == null ? "" : Palette.MUTED + Tr.t(", palier à ") + milestone));
        } else if (progress.best() > 1) {
            card.stat(Palette.MUTED, "✹", Tr.t("Série"), "0" + Palette.MUTED + " (record " + progress.best() + ")");
        }
        if (progress.bonus() > 0) {
            card.stat(Palette.SUCCESS, "✚", Tr.t("Bonus de récompenses"), "+" + progress.bonus() + "%");
        }
        KitShow show = KitShow.of(KitShow.levelFor(progress.kit(), progress.level()));
        card.stat(Palette.WARNING, "✦", Tr.t("Cérémonie"), show.name() + Palette.MUTED + " (niveau " + show.level() + "/"
                + KitShow.MAXIMUM + ")");
    }

    static void contents(Card card, Kit kit, Function<String, String> crateNames) {
        card.section(Tr.t("Contenu"));
        int armour = 0;
        for (Integer slot : kit.contents().keySet()) {
            if (KitSlots.armour(slot)) {
                armour++;
            }
        }
        int items = kit.contents().size();
        if (items > 0) {
            card.stat(Card.AMOUNT, Tr.t("Objets"), items + (armour > 0 ? Palette.MUTED + Tr.t(" dont ") + armour + Tr.t(" pièce(s) d'armure") : ""));
        }
        if (!kit.pool().empty()) {
            card.stat(Card.CHANCE, Tr.t("Tirage"), kit.pool().effectiveRolls() + Tr.t(" parmi ") + kit.pool().entries().size()
                    + Tr.t(" possibilités"));
        }
        KitRewards rewards = kit.rewards();
        if (rewards.money() > 0.0D) {
            card.money(Tr.t("Argent"), rewards.money());
        }
        if (rewards.shards() > 0L) {
            card.stat(Card.STAR, KitPoints.label(), Numbers.count(rewards.shards()));
        }
        if (rewards.levels() > 0) {
            card.stat(Card.STAR, Tr.t("Niveaux"), "+" + rewards.levels());
        }
        for (Map.Entry<String, Integer> key : rewards.keys().entrySet()) {
            card.stat(Card.FLAG, Tr.t("Clés"), key.getValue() + "x " + crateNames.apply(key.getKey()));
        }
        for (KitEffect effect : rewards.effects()) {
            card.stat(Card.ZONE, Tr.t("Effet"), effect.label() + Palette.MUTED + " " + Numbers.duration(effect.seconds() * 1_000L));
        }
        for (String line : rewards.lines()) {
            card.stat(Card.CALL, Tr.t("Bonus"), line);
        }
        if (kit.empty()) {
            card.line(Palette.MUTED + Tr.t("Rien pour le moment"));
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
        card.section(Tr.t("Conditions"));
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
        card.section(Tr.t("Règles"));
        for (KitStatus.Check check : status.checks()) {
            if (check.kind() == KitStatus.Kind.MONEY || check.kind() == KitStatus.Kind.SHARDS
                    || check.kind() == KitStatus.Kind.LEVELS) {
                card.raw(check(check));
            }
        }
        if (kit.cooldown() > 0L) {
            long effective = KitRules.cooldown(kit, status.reduction());
            String bonus = status.reduction() > 0 ? Palette.SUCCESS + " (-" + status.reduction() + Tr.t("% grâce à votre grade)") : "";
            card.stat(Card.TIME, Tr.t("Recharge"), Numbers.duration(effective) + bonus);
        }
        if (kit.reset() != KitReset.NONE) {
            card.stat(Card.TIME, Tr.t("Remise à zéro"), resetLabel(kit));
        }
        if (kit.limitedUses()) {
            card.stat(Card.FLAG, Tr.t("Utilisations"), status.usesLeft() + " / " + kit.maxUses() + Tr.t(" restantes"));
        }
        if (kit.limitedStock()) {
            card.stat(Card.ZONE, Tr.t("Stock serveur"), status.stockLeft() + " / " + kit.stock() + Tr.t(" restants"));
        }
    }

    public static String resetLabel(Kit kit) {
        String time = KitSchedule.TIME.format(kit.resetTime());
        return switch (kit.reset()) {
            case NONE -> "aucune";
            case DAILY -> Tr.t("chaque jour à ") + time;
            case WEEKLY -> Tr.t("chaque ") + KitSchedule.dayName(kit.resetDay()) + Tr.t(" à ") + time;
            case MONTHLY -> Tr.t("le 1er du mois à ") + time;
        };
    }
}
