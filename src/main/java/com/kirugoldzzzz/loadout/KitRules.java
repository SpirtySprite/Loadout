package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Tr;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class KitRules {

    private KitRules() {
    }

    public static int reduction(Kit kit, KitViewer viewer) {
        int best = 0;
        for (Map.Entry<String, Integer> entry : kit.reductions().entrySet()) {
            if (entry.getValue() != null && viewer.has(entry.getKey())) {
                best = Math.max(best, entry.getValue());
            }
        }
        return Math.max(0, Math.min(100, best));
    }

    public static int reduction(Kit kit, KitViewer viewer, int extra) {
        return Math.max(0, Math.min(100, reduction(kit, viewer) + Math.max(0, extra)));
    }

    public static long cooldown(Kit kit, int reduction) {
        if (kit.cooldown() <= 0L) {
            return 0L;
        }
        int kept = 100 - Math.max(0, Math.min(100, reduction));
        return kit.cooldown() / 100L * kept + kit.cooldown() % 100L * kept / 100L;
    }

    public static long readyAt(Kit kit, KitClaim claim, int reduction, ZoneId zone) {
        if (claim == null || claim.uses() <= 0 || claim.last() <= 0L) {
            return 0L;
        }
        long ready = 0L;
        long cooldown = cooldown(kit, reduction);
        if (cooldown > 0L) {
            ready = claim.last() + cooldown;
        }
        if (kit.reset() != KitReset.NONE) {
            ready = Math.max(ready, kit.reset().nextReset(claim.last(), zone, kit.resetTime(), kit.resetDay()));
        }
        return ready;
    }

    public static KitStatus evaluate(Kit kit, KitViewer viewer, KitClaim claim, int stockUsed, long now, ZoneId zone,
                                     Function<String, KitClaim> claims, Function<String, String> names) {
        return evaluate(kit, viewer, claim, stockUsed, now, zone, claims, names, kit.cost(), 0);
    }

    public static KitStatus evaluate(Kit kit, KitViewer viewer, KitClaim claim, int stockUsed, long now, ZoneId zone,
                                     Function<String, KitClaim> claims, Function<String, String> names, KitCost cost,
                                     int extraReduction) {
        List<KitStatus.Check> checks = new ArrayList<>();
        boolean bypassRequirements = viewer.has(KitViewer.BYPASS_REQUIREMENTS);
        boolean bypassCost = viewer.has(KitViewer.BYPASS_COST);
        boolean permitted = kit.permission() == null || viewer.has(kit.permission())
                || viewer.has(KitViewer.BYPASS_PERMISSION);
        if (kit.permission() != null) {
            checks.add(new KitStatus.Check(KitStatus.Kind.PERMISSION, permitted, Tr.t("Accès"),
                    permitted ? Tr.t("autorisé") : Tr.t("réservé")));
        }
        KitRequirements requirements = kit.requirements();
        KitSchedule schedule = requirements.schedule();
        boolean open = bypassRequirements || schedule.open(now, zone);
        if (!schedule.always()) {
            checks.add(new KitStatus.Check(KitStatus.Kind.SCHEDULE, open, Tr.t("Période"), scheduleDetail(schedule, now,
                    zone, open)));
        }
        if (requirements.playtime() > 0L) {
            boolean met = bypassRequirements || viewer.playtime() >= requirements.playtime();
            checks.add(new KitStatus.Check(KitStatus.Kind.PLAYTIME, met, Tr.t("Temps de jeu"),
                    Numbers.duration(Math.min(viewer.playtime(), requirements.playtime())) + " / "
                            + Numbers.duration(requirements.playtime())));
        }
        for (String required : requirements.kits()) {
            KitClaim previous = claims.apply(required);
            boolean met = bypassRequirements || (previous != null && previous.uses() > 0);
            String name = names.apply(required);
            checks.add(new KitStatus.Check(KitStatus.Kind.KITS, met, "Kit " + (name == null ? required : name),
                    met ? Tr.t("déjà récupéré") : Tr.t("à récupérer avant")));
        }
        if (!requirements.worlds().isEmpty()) {
            boolean met = bypassRequirements || (viewer.world() != null
                    && requirements.worlds().contains(viewer.world().toLowerCase(Locale.ROOT)));
            checks.add(new KitStatus.Check(KitStatus.Kind.WORLD, met, Tr.t("Monde"),
                    String.join(", ", requirements.worlds())));
        }
        if (requirements.balance() > 0.0D) {
            boolean met = bypassRequirements || viewer.balance() >= requirements.balance();
            checks.add(new KitStatus.Check(KitStatus.Kind.BALANCE, met, Tr.t("Solde minimum"),
                    Numbers.money(requirements.balance())));
        }
        if (requirements.level() > 0) {
            boolean met = bypassRequirements || viewer.level() >= requirements.level();
            checks.add(new KitStatus.Check(KitStatus.Kind.LEVEL, met, Tr.t("Niveau"),
                    Math.min(viewer.level(), requirements.level()) + " / " + requirements.level()));
        }
        for (KitStatistic statistic : requirements.statistics()) {
            long value = Math.max(0L, statistic.scale(viewer.statistics().applyAsLong(statistic)));
            boolean met = bypassRequirements || value >= statistic.amount();
            checks.add(new KitStatus.Check(KitStatus.Kind.STATISTIC, met, statistic.label(),
                    Numbers.count(Math.min(value, statistic.amount())) + " / " + Numbers.count(statistic.amount())));
        }
        for (KitCondition condition : requirements.conditions()) {
            boolean met = bypassRequirements || condition.test(viewer.placeholders().apply(condition.placeholder()));
            checks.add(new KitStatus.Check(KitStatus.Kind.CONDITION, met, condition.label(), condition.describe()));
        }
        if (kit.options().team()) {
            boolean met = bypassRequirements || viewer.team() != null;
            checks.add(new KitStatus.Check(KitStatus.Kind.TEAM, met, Tr.t("Équipe"), viewer.team() != null ? Tr.t("membre") : Tr.t("rejoindre une équipe")));
        }
        if (cost.money() > 0.0D) {
            boolean met = bypassCost || viewer.balance() >= cost.money();
            checks.add(new KitStatus.Check(KitStatus.Kind.MONEY, met, Tr.t("Prix"), Numbers.money(cost.money())));
        }
        if (cost.shards() > 0L) {
            boolean met = bypassCost || viewer.shards() >= cost.shards();
            checks.add(new KitStatus.Check(KitStatus.Kind.SHARDS, met, KitPoints.label(), Numbers.count(cost.shards())));
        }
        if (cost.levels() > 0) {
            boolean met = bypassCost || viewer.level() >= cost.levels();
            checks.add(new KitStatus.Check(KitStatus.Kind.LEVELS, met, Tr.t("Niveaux d'expérience"),
                    String.valueOf(cost.levels())));
        }

        int reduction = reduction(kit, viewer, extraReduction);
        long readyAt = readyAt(kit, claim, reduction, zone);
        long remaining = Math.max(0L, readyAt - now);
        int uses = claim == null ? 0 : claim.uses();
        int usesLeft = kit.limitedUses() ? Math.max(0, kit.maxUses() - uses) : -1;
        int stockLeft = kit.limitedStock() ? Math.max(0, kit.stock() - Math.max(0, stockUsed)) : -1;

        KitStatus.State state;
        if (!permitted) {
            state = KitStatus.State.LOCKED;
        } else if (!open) {
            state = KitStatus.State.CLOSED;
        } else if (stockLeft == 0) {
            state = KitStatus.State.SOLD_OUT;
        } else if (usesLeft == 0) {
            state = KitStatus.State.EXHAUSTED;
        } else if (remaining > 0L && !viewer.has(KitViewer.BYPASS_COOLDOWN)) {
            state = KitStatus.State.COOLDOWN;
        } else if (unmet(checks, KitStatus.Kind.PLAYTIME, KitStatus.Kind.KITS, KitStatus.Kind.WORLD,
                KitStatus.Kind.BALANCE, KitStatus.Kind.LEVEL, KitStatus.Kind.STATISTIC, KitStatus.Kind.CONDITION,
                KitStatus.Kind.TEAM)) {
            state = KitStatus.State.REQUIREMENTS;
        } else if (unmet(checks, KitStatus.Kind.MONEY, KitStatus.Kind.SHARDS, KitStatus.Kind.LEVELS)) {
            state = KitStatus.State.UNAFFORDABLE;
        } else {
            state = KitStatus.State.AVAILABLE;
        }
        if (viewer.has(KitViewer.BYPASS_COOLDOWN)) {
            remaining = 0L;
        }
        return new KitStatus(state, readyAt, remaining, usesLeft, stockLeft, reduction, checks);
    }

    private static boolean unmet(List<KitStatus.Check> checks, KitStatus.Kind... kinds) {
        for (KitStatus.Check check : checks) {
            if (check.met()) {
                continue;
            }
            for (KitStatus.Kind kind : kinds) {
                if (check.kind() == kind) {
                    return true;
                }
            }
        }
        return false;
    }

    static String scheduleDetail(KitSchedule schedule, long now, ZoneId zone, boolean open) {
        if (open) {
            return Tr.t("ouvert");
        }
        if (schedule.expired(now)) {
            return Tr.t("terminé");
        }
        long next = schedule.nextOpening(now, zone);
        return next <= 0L ? Tr.t("fermé") : Tr.t("ouvre dans ") + Numbers.duration(next - now);
    }
}
