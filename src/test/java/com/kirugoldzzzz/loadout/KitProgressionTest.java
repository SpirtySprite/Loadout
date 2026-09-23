package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.storage.Database;
import com.kirugoldzzzz.loadout.support.TestPlugin;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitProgressionTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final UUID PLAYER = new UUID(0L, 9L);
    private static final UUID TEAM = new UUID(5L, 5L);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final double EPSILON = 1.0E-9D;

    @TempDir
    Path folder;

    private static long at(int month, int day, int hour) {
        return LocalDateTime.of(2026, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static Kit kit(KitReset reset, KitOptions options, KitRequirements requirements, KitMastery mastery) {
        return new Kit("daily", "daily", List.of(), null, null, 0, null, 0L, reset, LocalTime.MIDNIGHT,
                DayOfWeek.MONDAY, 0, 0, KitCost.FREE, requirements, Map.of(), options, Map.of(), KitPool.EMPTY,
                KitRewards.NONE, false, List.of(), mastery, 0L, Kit.AUTOMATIC);
    }

    private static KitOptions teamOptions() {
        return new KitOptions(true, false, false, true, false, false, false, false, true, true, false, true, true, true);
    }

    private static KitMastery mastery() {
        return new KitMastery(List.of(
                new KitMastery.Tier("Or", 30, KitCost.FREE, 15, 35, 1, List.of()),
                new KitMastery.Tier("Bronze", 5, new KitCost(2500.0D, 0L, 0), 5, 10, 0, List.of()),
                new KitMastery.Tier("Argent", 15, new KitCost(7500.0D, 0L, 0), 10, 20, 0, List.of())));
    }

    @Test
    void masteryTiersUnlockByClaimsOrPurchase() {
        KitMastery mastery = mastery();
        assertEquals(List.of("Bronze", "Argent", "Or"), mastery.tiers().stream().map(KitMastery.Tier::name).toList());
        assertEquals(0, mastery.level(4, 0));
        assertEquals(1, mastery.level(5, 0));
        assertEquals(2, mastery.level(20, 0));
        assertEquals(3, mastery.level(1_000, 0));
        assertEquals(2, mastery.level(0, 2));
        assertEquals(3, mastery.level(0, 99));
        assertEquals(10, mastery.reduction(2));
        assertEquals(35, mastery.bonus(3));
        assertEquals(1, mastery.extraRolls(3));
        assertEquals(0, mastery.bonus(0));
        assertTrue(mastery.maxed(3));
        assertFalse(mastery.maxed(2));
        assertNull(mastery.next(3));
        assertEquals("Argent", mastery.next(1).name());
        assertEquals(0.5D, mastery.progress(10, 1), EPSILON);
        assertEquals(1.0D, mastery.progress(40, 3), EPSILON);
        assertTrue(mastery.next(0).purchasable());
        assertFalse(mastery.tier(3).purchasable());
        assertFalse(KitMastery.NONE.enabled());
    }

    @Test
    void streaksGrowOnConsecutivePeriodsAndBreakOnGaps() {
        Kit daily = kit(KitReset.DAILY, KitOptions.DEFAULTS, KitRequirements.NONE, KitMastery.NONE);
        long monday = at(9, 14, 10);
        assertEquals(1, KitProgression.nextStreak(daily, null, monday, UTC));
        KitClaim first = KitClaim.first(PLAYER, "daily", monday).withStreak(1);
        assertEquals(2, KitProgression.nextStreak(daily, first, monday + DAY, UTC));
        KitClaim second = first.again(monday + DAY).withStreak(2);
        assertEquals(3, KitProgression.nextStreak(daily, second, at(9, 16, 23), UTC));
        assertEquals(1, KitProgression.nextStreak(daily, second, at(9, 17, 1), UTC));
        assertEquals(2, second.best());

        assertEquals(2, KitProgression.liveStreak(daily, second, at(9, 15, 20), UTC));
        assertEquals(2, KitProgression.liveStreak(daily, second, at(9, 16, 20), UTC));
        assertEquals(0, KitProgression.liveStreak(daily, second, at(9, 17, 20), UTC));

        assertTrue(KitProgression.streakAtRisk(daily, second, at(9, 16, 22), UTC, 3 * HOUR));
        assertFalse(KitProgression.streakAtRisk(daily, second, at(9, 16, 12), UTC, 3 * HOUR));
        assertFalse(KitProgression.streakAtRisk(daily, second, at(9, 15, 22), UTC, 3 * HOUR));
        assertFalse(KitProgression.streakAtRisk(daily, first, at(9, 15, 22), UTC, 3 * HOUR));

        Kit timeless = kit(KitReset.NONE, KitOptions.DEFAULTS, KitRequirements.NONE, KitMastery.NONE);
        assertEquals(0, KitProgression.nextStreak(timeless, first, monday + DAY, UTC));

        KitProgression.Streaks streaks = new KitProgression.Streaks(true, 5, 30, HOUR,
                Map.of(7, KitRewards.NONE, 3, KitRewards.NONE));
        assertEquals(0, streaks.bonus(1));
        assertEquals(10, streaks.bonus(3));
        assertEquals(30, streaks.bonus(40));
        assertEquals(3, streaks.nextMilestone(1));
        assertEquals(7, streaks.nextMilestone(3));
        assertNull(streaks.nextMilestone(7));
    }

    @Test
    void featuredKitRotatesDailyAtTheConfiguredHour() {
        KitProgression.Featured featured = new KitProgression.Featured(true, 30, List.of(), LocalTime.of(6, 0));
        List<String> candidates = List.of("a", "b", "c");
        String early = featured.pick(candidates, at(9, 15, 5), UTC);
        String later = featured.pick(candidates, at(9, 15, 7), UTC);
        String nextDay = featured.pick(candidates, at(9, 16, 7), UTC);
        assertNotNull(early);
        assertNotEquals(early, later);
        assertNotEquals(later, nextDay);
        assertEquals(later, featured.pick(candidates, at(9, 16, 5), UTC));
        assertEquals(at(9, 16, 6), featured.nextRotation(at(9, 15, 7), UTC));
        assertNull(KitProgression.Featured.NONE.pick(candidates, at(9, 15, 7), UTC));
        assertNull(featured.pick(List.of(), at(9, 15, 7), UTC));
        assertEquals(90, new KitProgression.Featured(true, 150, List.of(), null).discount());
    }

    @Test
    void statisticsParseAndDriveChallenges() {
        KitStatistic kills = KitStatistic.parse("mob_kills:250");
        assertEquals(Statistic.MOB_KILLS, kills.statistic());
        assertEquals(250L, kills.amount());
        assertEquals("Monstres tués", kills.label());
        KitStatistic creepers = KitStatistic.parse("kill_entity:creeper:25");
        assertEquals(EntityType.CREEPER, creepers.entity());
        assertEquals("Créatures tuées (Creeper)", creepers.label());
        assertEquals(creepers, KitStatistic.parse(creepers.write()));
        KitStatistic diamonds = KitStatistic.parse("mine_block:diamond_ore:50");
        assertEquals(Material.DIAMOND_ORE, diamonds.material());
        KitStatistic walk = KitStatistic.parse("walk_one_cm:1000");
        assertTrue(walk.distance());
        assertEquals(12L, walk.scale(1_250L));
        assertNull(KitStatistic.parse("mob_kills"));
        assertNull(KitStatistic.parse("mine_block:500"));
        assertNull(KitStatistic.parse("kill_entity:dragonfly:5"));
        assertNull(KitStatistic.parse("nothing:5"));
        assertNull(KitStatistic.parse("mob_kills:-3"));

        KitRequirements requirements = new KitRequirements(0L, List.of(), java.util.Set.of(), 0.0D, 0,
                KitSchedule.ALWAYS, List.of(kills, walk));
        Kit hunter = kit(KitReset.NONE, KitOptions.DEFAULTS, requirements, KitMastery.NONE);
        KitViewer weak = new KitViewer(PLAYER, ignored -> false, 0L, 0.0D, 0L, 0, "world", null,
                statistic -> statistic.statistic() == Statistic.MOB_KILLS ? 40L : 200_000L);
        KitStatus missing = KitRules.evaluate(hunter, weak, null, 0, at(9, 15, 12), UTC, ignored -> null,
                ignored -> null);
        assertEquals(KitStatus.State.REQUIREMENTS, missing.state());
        assertTrue(missing.unmet(KitStatus.Kind.STATISTIC));
        assertTrue(missing.checks().stream().anyMatch(check -> check.detail().startsWith("40")));
        KitViewer strong = new KitViewer(PLAYER, ignored -> false, 0L, 0.0D, 0L, 0, "world", null,
                statistic -> 1_000_000L);
        assertTrue(KitRules.evaluate(hunter, strong, null, 0, at(9, 15, 12), UTC, ignored -> null, ignored -> null)
                .available());
    }

    @Test
    void teamKitsNeedATeamAndShareTheirOwner() {
        Kit shared = kit(KitReset.DAILY, teamOptions(), KitRequirements.NONE, KitMastery.NONE);
        KitViewer alone = new KitViewer(PLAYER, ignored -> false, 0L, 0.0D, 0L, 0, "world", null, null);
        KitViewer member = new KitViewer(PLAYER, ignored -> false, 0L, 0.0D, 0L, 0, "world", TEAM, null);
        assertEquals(PLAYER, alone.owner(shared));
        assertEquals(TEAM, member.owner(shared));
        assertEquals(PLAYER, member.owner(kit(KitReset.DAILY, KitOptions.DEFAULTS, KitRequirements.NONE,
                KitMastery.NONE)));
        long now = at(9, 15, 12);
        assertEquals(KitStatus.State.REQUIREMENTS, KitRules.evaluate(shared, alone, null, 0, now, UTC,
                ignored -> null, ignored -> null).state());
        KitClaim teamClaim = KitClaim.first(TEAM, "daily", now - HOUR);
        assertEquals(KitStatus.State.COOLDOWN, KitRules.evaluate(shared, member, teamClaim, 0, now, UTC,
                ignored -> null, ignored -> null).state());
    }

    @Test
    void discountsAndMasteryReductionsFlowThroughTheRules() {
        Kit paid = new Kit("paid", "paid", List.of(), null, null, 0, null, DAY, KitReset.NONE, LocalTime.MIDNIGHT,
                DayOfWeek.MONDAY, 0, 0, new KitCost(1_000.0D, 0L, 0), KitRequirements.NONE, Map.of("vip", 20),
                KitOptions.DEFAULTS, Map.of(), KitPool.EMPTY, KitRewards.NONE, false, List.of(), KitMastery.NONE,
                0L, Kit.AUTOMATIC);
        long now = at(9, 15, 12);
        KitViewer vip = new KitViewer(PLAYER, "vip"::equals, 0L, 800.0D, 0L, 0, "world");
        KitClaim claim = KitClaim.first(PLAYER, "paid", now - HOUR);
        KitStatus status = KitRules.evaluate(paid, vip, claim, 0, now, UTC, ignored -> null, ignored -> null,
                new KitCost(700.0D, 0L, 0), 15);
        assertEquals(35, status.reduction());
        assertEquals(DAY * 65 / 100 - HOUR, status.remaining());
        KitStatus affordable = KitRules.evaluate(paid, vip, null, 0, now, UTC, ignored -> null, ignored -> null,
                new KitCost(700.0D, 0L, 0), 0);
        assertTrue(affordable.available());
        assertEquals(KitStatus.State.UNAFFORDABLE, KitRules.evaluate(paid, vip, null, 0, now, UTC, ignored -> null,
                ignored -> null).state());
    }

    @Test
    void progressionLoadsFromYamlAndMasteryTemplateApplies() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                progression:
                  mastery:
                    bronze: {name: Bronze, claims: 5, bonus: 10, upgrade: {money: 100}}
                    rate: {name: Rien}
                  streaks:
                    bonus-per-claim: 4
                    maximum-bonus: 40
                    warning: 2h
                    milestones:
                      '7': {money: 700}
                  collection:
                    '3': {money: 300, levels: 3}
                  featured:
                    discount: 20
                    rotation: "06:00"
                    kits: [paye, fantome]
                kits:
                  paye:
                    cost: {money: 50}
                  gratuit:
                    cooldown: 1h
                  unique:
                    max-uses: 1
                  propre:
                    mastery:
                      maitre: {name: Maître, claims: 2}
                  sans:
                    options: {mastery: false}
                """);
        KitCatalog catalog = KitLoader.read(yaml, ignored -> null);
        KitProgression progression = catalog.progression();
        assertEquals(1, progression.mastery().tiers().size());
        assertEquals(100.0D, progression.mastery().tiers().getFirst().upgrade().money());
        assertEquals(4, progression.streaks().bonusPerClaim());
        assertEquals(2 * HOUR, progression.streaks().warning());
        assertEquals(700.0D, progression.streaks().milestones().get(7).money());
        assertEquals(3, progression.collection().get(3).levels());
        assertEquals(List.of("paye"), progression.featured().kits());
        assertEquals(LocalTime.of(6, 0), progression.featured().rotation());
        assertTrue(catalog.problems().stream().anyMatch(problem -> problem.contains("fantome")));
        assertTrue(catalog.problems().stream().anyMatch(problem -> problem.contains("rate")));
        assertEquals(List.of("paye"), catalog.featuredCandidates());

        assertEquals("Bronze", catalog.masteryOf(catalog.kit("gratuit").orElseThrow()).tiers().getFirst().name());
        assertFalse(catalog.masteryOf(catalog.kit("unique").orElseThrow()).enabled());
        assertEquals("Maître", catalog.masteryOf(catalog.kit("propre").orElseThrow()).tiers().getFirst().name());
        assertFalse(catalog.masteryOf(catalog.kit("sans").orElseThrow()).enabled());
    }

    @Test
    void repositoryKeepsTiersStreaksFavoritesAndMilestones() throws Exception {
        Database database = new Database(TestPlugin.at(folder.toFile()), "progression.db");
        database.open();
        try {
            KitRepository repository = new KitRepository(database);
            repository.load();
            repository.record(PLAYER, "daily", 1_000L, 1);
            repository.record(PLAYER, "daily", 2_000L, 2);
            repository.setTier(PLAYER, "daily", 3);
            repository.setTier(PLAYER, "fresh", 1);
            assertTrue(repository.toggleFavorite(PLAYER, "daily"));
            assertTrue(repository.toggleFavorite(PLAYER, "other"));
            assertFalse(repository.toggleFavorite(PLAYER, "other"));
            assertTrue(repository.grantMilestone(PLAYER, "collection:3"));
            assertFalse(repository.grantMilestone(PLAYER, "collection:3"));
            repository.flush();

            KitRepository fresh = new KitRepository(database);
            fresh.load();
            KitClaim daily = fresh.find(PLAYER, "daily");
            assertEquals(2, daily.uses());
            assertEquals(2, daily.streak());
            assertEquals(2, daily.best());
            assertEquals(3, daily.tier());
            KitClaim tierOnly = fresh.find(PLAYER, "fresh");
            assertEquals(0, tierOnly.uses());
            assertEquals(1, tierOnly.tier());
            assertEquals(0L, KitRules.readyAt(kit(KitReset.DAILY, KitOptions.DEFAULTS, KitRequirements.NONE,
                    KitMastery.NONE), tierOnly, 0, UTC));
            assertEquals(1, fresh.distinct(PLAYER));
            assertEquals(java.util.Set.of("daily"), fresh.favorites(PLAYER));
            assertEquals(java.util.Set.of("collection:3"), fresh.milestones(PLAYER));
            assertEquals(2, fresh.stats("daily").bestStreak());

            KitClaim undone = fresh.undo(PLAYER, "fresh");
            assertEquals(0, undone.uses());
            assertEquals(1, fresh.resetMilestones(PLAYER));
            assertEquals(1, fresh.forget("daily"));
            fresh.flush();
            KitRepository after = new KitRepository(database);
            after.load();
            assertNull(after.find(PLAYER, "daily"));
            assertTrue(after.favorites(PLAYER).isEmpty());
            assertTrue(after.milestones(PLAYER).isEmpty());
        } finally {
            database.close();
        }
    }
}
