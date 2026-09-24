package com.kirugoldzzzz.loadout;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitConditionTest {

    private static final UUID PLAYER = new UUID(0L, 42L);

    @Test
    void numbersCompareAsNumbers() {
        KitCondition level = KitCondition.parse("%player_level% >= 10", null);
        assertTrue(level.test("10"));
        assertTrue(level.test("1,250"));
        assertFalse(level.test("9.5"));
        assertFalse(level.test("not a number"));
        assertTrue(KitCondition.parse("%x% < 3", null).test("2"));
        assertTrue(KitCondition.parse("%x% != 3", null).test("4"));
    }

    @Test
    void textComparesWithoutCase() {
        KitCondition group = KitCondition.parse("%luckperms_primary_group_name% == \"VIP\"", "VIP rank");
        assertEquals("VIP rank", group.label());
        assertTrue(group.test("vip"));
        assertFalse(group.test("default"));
        assertTrue(KitCondition.parse("%player_world% contains nether", null).test("world_Nether"));
        assertFalse(KitCondition.parse("%player_name% > abc", null).test("zzz"));
    }

    @Test
    void labelsAreReadableAndInvalidShapesRejected() {
        assertEquals("Player level", KitCondition.parse("%player_level% >= 10", null).label());
        assertEquals("≥ 10", KitCondition.parse("%player_level% >= 10", null).describe());
        assertNull(KitCondition.parse("player_level >= 10", null));
        assertNull(KitCondition.parse("%player_level%", null));
        assertNull(KitCondition.parse(null, null));
    }

    @Test
    void pointBalancesParseFormattedNumbers() {
        assertEquals(1250L, KitPoints.parse("1,250"));
        assertEquals(12L, KitPoints.parse("12.9 points"));
        assertEquals(0L, KitPoints.parse("%playerpoints_points%"));
        assertEquals(0L, KitPoints.parse(null));
    }

    @Test
    void unmetConditionsLockTheKitUntilThePlaceholderMatches() {
        KitRequirements requirements = new KitRequirements(0L, List.of(), Set.of(), 0.0D, 0, KitSchedule.ALWAYS,
                List.of(), List.of(KitCondition.parse("%vault_rank% == vip", "VIP rank")));
        Kit kit = new Kit("vip", "vip", List.of(), null, null, 0, null, 0L, KitReset.NONE, LocalTime.MIDNIGHT,
                DayOfWeek.MONDAY, 0, 0, KitCost.FREE, requirements, Map.of(), KitOptions.DEFAULTS, Map.of(),
                KitPool.EMPTY, KitRewards.NONE, false, List.of(), KitMastery.NONE, 0L, Kit.AUTOMATIC);

        KitStatus locked = KitRules.evaluate(kit, viewer("default"), null, 0, 0L, ZoneOffset.UTC, ignored -> null,
                ignored -> null);
        assertEquals(KitStatus.State.REQUIREMENTS, locked.state());
        assertTrue(locked.checks().stream().anyMatch(check -> check.kind() == KitStatus.Kind.CONDITION && !check.met()));

        assertTrue(KitRules.evaluate(kit, viewer("VIP"), null, 0, 0L, ZoneOffset.UTC, ignored -> null,
                ignored -> null).available());
    }

    private static KitViewer viewer(String rank) {
        return new KitViewer(PLAYER, ignored -> false, 0L, 0.0D, 0L, 0, "world", null, null,
                raw -> raw.equals("%vault_rank%") ? rank : raw);
    }
}
