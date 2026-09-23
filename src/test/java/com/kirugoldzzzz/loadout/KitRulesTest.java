package com.kirugoldzzzz.loadout;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitRulesTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final UUID PLAYER = new UUID(0L, 7L);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final double EPSILON = 1.0E-9D;

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static Kit kit(String id, String permission, long cooldown, KitReset reset, int maxUses, int stock,
                           KitCost cost, KitRequirements requirements, Map<String, Integer> reductions) {
        return new Kit(id, id, List.of(), null, null, 0, permission, cooldown, reset, LocalTime.MIDNIGHT,
                DayOfWeek.MONDAY, maxUses, stock, cost, requirements, reductions, KitOptions.DEFAULTS, Map.of(),
                KitPool.EMPTY, KitRewards.NONE, false, List.of(), KitMastery.NONE, 0L, Kit.AUTOMATIC);
    }

    private static KitViewer viewer(Predicate<String> permissions, long playtime, double balance, int level) {
        return new KitViewer(PLAYER, permissions, playtime, balance, 0L, level, "world");
    }

    private static KitStatus evaluate(Kit kit, KitViewer viewer, KitClaim claim, int stockUsed, long now) {
        return KitRules.evaluate(kit, viewer, claim, stockUsed, now, UTC, ignored -> null, ignored -> null);
    }

    @Test
    void durationsParseFrenchAndEnglishUnits() {
        assertEquals(90_000L, KitDurations.parse("1m 30s").orElseThrow());
        assertEquals(DAY + 2 * HOUR, KitDurations.parse("1j2h").orElseThrow());
        assertEquals(7 * DAY, KitDurations.parse("1 semaine").orElseThrow());
        assertEquals(45_000L, KitDurations.parse("45").orElseThrow());
        assertEquals(0L, KitDurations.parse("aucun").orElseThrow());
        assertEquals(3 * DAY, KitDurations.parse("3d").orElseThrow());
        assertTrue(KitDurations.parse("abc").isEmpty());
        assertTrue(KitDurations.parse("5x").isEmpty());
        assertTrue(KitDurations.parse("").isEmpty());
        assertTrue(KitDurations.parse("9999999 semaines").isEmpty());
        assertEquals("1j 2h 3m 4s", KitDurations.write(DAY + 2 * HOUR + 3 * 60_000L + 4_000L));
        assertEquals(DAY + 2 * HOUR, KitDurations.parse(KitDurations.write(DAY + 2 * HOUR)).orElseThrow());
        assertEquals("0", KitDurations.write(0L));
    }

    @Test
    void resetsFollowTheCalendar() {
        long wednesdayNoon = at(2026, 9, 16, 12, 0);
        assertEquals(at(2026, 9, 16, 0, 0), KitReset.DAILY.periodStart(wednesdayNoon, UTC, LocalTime.MIDNIGHT, null));
        assertEquals(at(2026, 9, 17, 0, 0), KitReset.DAILY.nextReset(wednesdayNoon, UTC, LocalTime.MIDNIGHT, null));
        assertEquals(at(2026, 9, 15, 18, 0), KitReset.DAILY.periodStart(wednesdayNoon - 13 * HOUR, UTC,
                LocalTime.of(18, 0), null));
        assertEquals(at(2026, 9, 14, 0, 0), KitReset.WEEKLY.periodStart(wednesdayNoon, UTC, LocalTime.MIDNIGHT,
                DayOfWeek.MONDAY));
        assertEquals(at(2026, 9, 21, 0, 0), KitReset.WEEKLY.nextReset(wednesdayNoon, UTC, LocalTime.MIDNIGHT,
                DayOfWeek.MONDAY));
        assertEquals(at(2026, 10, 1, 6, 0), KitReset.MONTHLY.nextReset(wednesdayNoon, UTC, LocalTime.of(6, 0), null));
        assertEquals(KitReset.WEEKLY, KitReset.parse("hebdomadaire"));
        assertEquals(KitReset.NONE, KitReset.parse("n'importe quoi"));
        assertEquals(KitReset.DAILY, KitReset.NONE.next());
    }

    @Test
    void schedulesOpenOnDaysHoursAndDates() {
        long saturdayEvening = at(2026, 9, 19, 20, 0);
        KitSchedule weekend = new KitSchedule(0L, 0L, Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                LocalTime.of(18, 0), LocalTime.of(23, 0));
        assertTrue(weekend.open(saturdayEvening, UTC));
        assertFalse(weekend.open(saturdayEvening + 4 * HOUR, UTC));
        assertFalse(weekend.open(at(2026, 9, 16, 20, 0), UTC));
        assertEquals(at(2026, 9, 19, 18, 0), weekend.nextOpening(at(2026, 9, 16, 20, 7), UTC));

        KitSchedule night = new KitSchedule(0L, 0L, Set.of(), LocalTime.of(22, 0), LocalTime.of(2, 0));
        assertTrue(night.open(at(2026, 9, 16, 23, 30), UTC));
        assertTrue(night.open(at(2026, 9, 17, 1, 0), UTC));
        assertFalse(night.open(at(2026, 9, 17, 3, 0), UTC));

        KitSchedule event = new KitSchedule(at(2026, 12, 24, 0, 0), at(2026, 12, 27, 0, 0), Set.of(), null, null);
        assertFalse(event.open(at(2026, 12, 23, 23, 0), UTC));
        assertTrue(event.open(at(2026, 12, 25, 12, 0), UTC));
        assertTrue(event.expired(at(2026, 12, 27, 0, 0)));
        assertEquals(0L, event.nextOpening(at(2026, 12, 28, 0, 0), UTC));

        assertEquals(DayOfWeek.FRIDAY, KitSchedule.parseDay("vendredi"));
        assertEquals(LocalTime.of(18, 0), KitSchedule.parseTime("18h"));
        assertEquals(LocalTime.of(9, 30), KitSchedule.parseTime("9h30"));
        assertEquals(at(2026, 12, 24, 0, 0), KitSchedule.parseDate("24/12/2026", UTC));
        assertEquals(-1L, KitSchedule.parseDate("demain", UTC));
        assertEquals("18:00-23:00", weekend.writeHours());
        assertTrue(KitSchedule.ALWAYS.always());
    }

    @Test
    void cooldownsAndReductionsDecideReadiness() {
        Kit kit = kit("daily", null, DAY, KitReset.NONE, 0, 0, KitCost.FREE, KitRequirements.NONE,
                Map.of("vip", 25, "legend", 50));
        long now = at(2026, 9, 16, 12, 0);
        KitClaim claim = KitClaim.first(PLAYER, "daily", now - HOUR);
        KitStatus waiting = evaluate(kit, viewer(ignored -> false, 0L, 0.0D, 0), claim, 0, now);
        assertEquals(KitStatus.State.COOLDOWN, waiting.state());
        assertEquals(DAY - HOUR, waiting.remaining());

        KitViewer legend = viewer(permission -> permission.equals("vip") || permission.equals("legend"), 0L, 0.0D, 0);
        KitStatus shortened = evaluate(kit, legend, claim, 0, now);
        assertEquals(50, shortened.reduction());
        assertEquals(DAY / 2 - HOUR, shortened.remaining());

        KitStatus ready = evaluate(kit, viewer(ignored -> false, 0L, 0.0D, 0), claim, 0, now + DAY);
        assertTrue(ready.available());

        KitStatus bypass = evaluate(kit, viewer(KitViewer.BYPASS_COOLDOWN::equals, 0L, 0.0D, 0), claim, 0, now);
        assertTrue(bypass.available());
        assertEquals(0L, bypass.remaining());

        Kit dailyReset = kit("reset", null, 0L, KitReset.DAILY, 0, 0, KitCost.FREE, KitRequirements.NONE, Map.of());
        KitClaim late = KitClaim.first(PLAYER, "reset", at(2026, 9, 16, 23, 50));
        assertEquals(KitStatus.State.COOLDOWN, evaluate(dailyReset, viewer(ignored -> false, 0L, 0.0D, 0), late, 0,
                at(2026, 9, 16, 23, 55)).state());
        assertTrue(evaluate(dailyReset, viewer(ignored -> false, 0L, 0.0D, 0), late, 0, at(2026, 9, 17, 0, 1))
                .available());
        assertEquals(0L, KitRules.readyAt(dailyReset, null, 0, UTC));
        assertEquals(DAY * 3 / 4, KitRules.cooldown(kit, 25));
    }

    @Test
    void limitsStockPermissionsAndRequirementsHaveAClearOrder() {
        long now = at(2026, 9, 16, 12, 0);
        KitViewer nobody = viewer(ignored -> false, HOUR, 100.0D, 3);

        Kit locked = kit("vip", "loadout.kit.vip", 0L, KitReset.NONE, 0, 0, KitCost.FREE, KitRequirements.NONE,
                Map.of());
        assertEquals(KitStatus.State.LOCKED, evaluate(locked, nobody, null, 0, now).state());
        assertTrue(evaluate(locked, viewer("loadout.kit.vip"::equals, 0L, 0.0D, 0), null, 0, now).available());

        Kit once = kit("once", null, 0L, KitReset.NONE, 1, 0, KitCost.FREE, KitRequirements.NONE, Map.of());
        KitStatus used = evaluate(once, nobody, KitClaim.first(PLAYER, "once", now - DAY), 0, now);
        assertEquals(KitStatus.State.EXHAUSTED, used.state());
        assertEquals(0, used.usesLeft());
        assertEquals(1, evaluate(once, nobody, null, 0, now).usesLeft());

        Kit limited = kit("stock", null, 0L, KitReset.NONE, 0, 10, KitCost.FREE, KitRequirements.NONE, Map.of());
        assertEquals(KitStatus.State.SOLD_OUT, evaluate(limited, nobody, null, 10, now).state());
        assertEquals(3, evaluate(limited, nobody, null, 7, now).stockLeft());

        KitRequirements veteran = new KitRequirements(5 * HOUR, List.of("starter"), Set.of("world"), 50.0D, 2,
                KitSchedule.ALWAYS);
        Kit demanding = kit("veteran", null, 0L, KitReset.NONE, 0, 0, KitCost.FREE, veteran, Map.of());
        KitStatus missing = evaluate(demanding, nobody, null, 0, now);
        assertEquals(KitStatus.State.REQUIREMENTS, missing.state());
        assertTrue(missing.unmet(KitStatus.Kind.PLAYTIME));
        assertTrue(missing.unmet(KitStatus.Kind.KITS));
        assertFalse(missing.unmet(KitStatus.Kind.WORLD));
        assertFalse(missing.unmet(KitStatus.Kind.BALANCE));
        KitStatus met = KitRules.evaluate(demanding, viewer(ignored -> false, 6 * HOUR, 60.0D, 2), null, 0, now, UTC,
                kit -> kit.equals("starter") ? KitClaim.first(PLAYER, kit, now - DAY) : null, kit -> "Débutant");
        assertTrue(met.available());
        assertTrue(evaluate(demanding, viewer(KitViewer.BYPASS_REQUIREMENTS::equals, 0L, 0.0D, 0), null, 0, now)
                .available());

        Kit paid = kit("paid", null, 0L, KitReset.NONE, 0, 0, new KitCost(500.0D, 0L, 5), KitRequirements.NONE,
                Map.of());
        KitStatus poor = evaluate(paid, nobody, null, 0, now);
        assertEquals(KitStatus.State.UNAFFORDABLE, poor.state());
        assertTrue(poor.unmet(KitStatus.Kind.MONEY));
        assertTrue(poor.unmet(KitStatus.Kind.LEVELS));
        assertTrue(evaluate(paid, viewer(KitViewer.BYPASS_COST::equals, 0L, 0.0D, 0), null, 0, now).available());

        KitRequirements weekend = new KitRequirements(0L, List.of(), Set.of(), 0.0D, 0,
                new KitSchedule(0L, 0L, Set.of(DayOfWeek.SATURDAY), null, null));
        Kit event = kit("event", null, 0L, KitReset.NONE, 0, 0, KitCost.FREE, weekend, Map.of());
        assertEquals(KitStatus.State.CLOSED, evaluate(event, nobody, null, 0, now).state());
    }

    @Test
    void poolsRollWithinWeightsAndReportExactChances() {
        KitPool.Entry common = new KitPool.Entry("common", null, 3, 1, 1);
        KitPool.Entry rare = new KitPool.Entry("rare", null, 1, 2, 4);
        KitPool once = new KitPool(1, true, List.of(common, rare));
        assertEquals(0.25D, once.chance(rare), EPSILON);
        assertEquals(0.75D, once.chance(common), EPSILON);

        KitPool both = new KitPool(2, true, List.of(common, rare));
        assertEquals(1.0D, both.chance(rare), EPSILON);

        KitPool.Entry third = new KitPool.Entry("third", null, 1, 1, 1);
        KitPool unique = new KitPool(2, true, List.of(common, rare, third));
        double sum = unique.chance(common) + unique.chance(rare) + unique.chance(third);
        assertEquals(2.0D, sum, EPSILON);
        assertEquals(0.6D + 0.2D * 0.75D * 2.0D, unique.chance(common), EPSILON);

        KitPool repeated = new KitPool(2, false, List.of(common, rare));
        assertEquals(1.0D - 0.75D * 0.75D, repeated.chance(rare), EPSILON);

        SplittableRandom random = new SplittableRandom(42L);
        int rares = 0;
        for (int round = 0; round < 4_000; round++) {
            List<KitPool.Pull> pulls = once.roll(random);
            assertEquals(1, pulls.size());
            KitPool.Pull pull = pulls.getFirst();
            if (pull.entry() == rare) {
                rares++;
                assertTrue(pull.amount() >= 2 && pull.amount() <= 4);
            } else {
                assertEquals(1, pull.amount());
            }
        }
        assertTrue(rares > 850 && rares < 1_150, "tirages rares: " + rares);
        List<KitPool.Pull> distinct = unique.roll(random);
        assertEquals(2, distinct.size());
        assertTrue(distinct.get(0).entry() != distinct.get(1).entry());
        assertTrue(KitPool.EMPTY.roll(random).isEmpty());
    }

    @Test
    void commandsEffectsAndSlotsParse() {
        KitDispatch console = KitDispatch.parse("console: /eco give <player> 10");
        assertTrue(console.console());
        assertEquals("eco give Bob 10", console.resolve("Bob", "id", "starter"));
        KitDispatch player = KitDispatch.parse("joueur: spawn");
        assertFalse(player.console());
        assertEquals("joueur: spawn", player.write());
        assertFalse(KitDispatch.parse("   ").valid());

        KitEffect speed = KitEffect.parse("speed:2m:2");
        assertEquals("speed", speed.type());
        assertEquals(120, speed.seconds());
        assertEquals(1, speed.amplifier());
        assertEquals("Speed II", speed.label());
        assertEquals(speed, KitEffect.parse(speed.write()));
        assertEquals(30, KitEffect.parse("minecraft:haste").seconds());
        assertNull(KitEffect.parse("speed:jamais"));

        for (int slot = 0; slot < KitSlots.SIZE; slot++) {
            assertEquals(slot, KitSlots.kitSlot(KitSlots.editorSlot(slot)));
        }
        assertEquals("Casque", KitSlots.label(KitSlots.HELMET));
        assertTrue(KitSlots.armour(KitSlots.BOOTS));
        assertFalse(KitSlots.armour(KitSlots.OFFHAND));
    }
}
