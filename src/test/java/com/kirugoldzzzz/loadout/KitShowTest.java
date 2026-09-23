package com.kirugoldzzzz.loadout;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitShowTest {

    private static Kit kit(Map<Integer, org.bukkit.inventory.ItemStack> contents, KitPool pool, KitRewards rewards,
                           KitCost cost) {
        return kit(contents, pool, rewards, cost, Kit.AUTOMATIC);
    }

    private static Kit kit(Map<Integer, org.bukkit.inventory.ItemStack> contents, KitPool pool, KitRewards rewards,
                           KitCost cost, int animationLevel) {
        return new Kit("show", "show", List.of(), null, null, 0, null, 0L, KitReset.NONE, LocalTime.MIDNIGHT,
                DayOfWeek.MONDAY, 0, 0, cost, KitRequirements.NONE, Map.of(), KitOptions.DEFAULTS, contents, pool,
                rewards, false, List.of(), KitMastery.NONE, 0L, animationLevel);
    }

    @Test
    void ceremonyLevelGrowsWithRichnessAndMastery() {
        Kit plain = kit(Map.of(), KitPool.EMPTY, KitRewards.NONE, KitCost.FREE);
        assertEquals(0, KitShow.levelFor(plain, 0));
        assertEquals(2, KitShow.levelFor(plain, 2));
        assertEquals(KitShow.MAXIMUM, KitShow.levelFor(plain, 9));

        KitPool pool = new KitPool(1, true, List.of(new KitPool.Entry("a", null, 1, 1, 1)));
        Kit rich = kit(Map.of(), pool, new KitRewards(KitShow.RICH_VALUE, 0L, 0, Map.of(), List.of(),
                List.of(), List.of(), List.of()), KitCost.FREE);
        assertEquals(2, KitShow.levelFor(rich, 0));
        assertEquals(3, KitShow.levelFor(rich, 1));
        assertEquals(KitShow.MAXIMUM, KitShow.levelFor(rich, 2));
        assertEquals(2, KitShow.richness(kit(Map.of(), pool,
                new KitRewards(KitShow.RICH_VALUE, 9L, 0, Map.of("caisse", 2), List.of(), List.of(), List.of(),
                        List.of()), new KitCost(KitShow.RICH_VALUE, 0L, 0))));
        assertTrue(KitShow.richness(rich) <= KitShow.RICHNESS_CAP);

        Kit paid = kit(Map.of(), KitPool.EMPTY, KitRewards.NONE, new KitCost(KitShow.RICH_VALUE, 0L, 0));
        assertEquals(1, KitShow.levelFor(paid, 0));
        Kit shards = kit(Map.of(), KitPool.EMPTY, new KitRewards(0.0D, 5L, 0, Map.of(), List.of(), List.of(),
                List.of(), List.of()), KitCost.FREE);
        assertEquals(1, KitShow.levelFor(shards, 0));
    }

    @Test
    void aForcedAnimationLevelWinsOverRichnessAndMastery() {
        Kit forced = kit(Map.of(), KitPool.EMPTY, KitRewards.NONE, KitCost.FREE, 4);
        assertEquals(4, KitShow.levelFor(forced, 0));
        assertEquals(4, KitShow.levelFor(forced, 5));
        Kit quiet = kit(Map.of(), new KitPool(1, true, List.of(new KitPool.Entry("a", null, 1, 1, 1))),
                new KitRewards(9_999.0D, 10L, 0, Map.of(), List.of(), List.of(), List.of(), List.of()), KitCost.FREE, 0);
        assertEquals(0, KitShow.levelFor(quiet, 5));
        assertEquals(KitShow.MAXIMUM, kit(Map.of(), KitPool.EMPTY, KitRewards.NONE, KitCost.FREE, 99).animationLevel());
        assertEquals(Kit.AUTOMATIC, kit(Map.of(), KitPool.EMPTY, KitRewards.NONE, KitCost.FREE, -7).animationLevel());
    }

    @Test
    void everyLevelHasItsOwnPaletteCastAndName() {
        Set<String> names = new HashSet<>();
        Set<Object> palettes = new HashSet<>();
        KitShow previous = null;
        for (int level = 0; level <= KitShow.MAXIMUM; level++) {
            KitShow show = KitShow.of(level);
            assertEquals(level, show.level());
            assertTrue(names.add(show.name()), "nom réutilisé " + show.name());
            assertTrue(palettes.add(List.of(show.primary(), show.accent())), "palette réutilisée au niveau " + level);
            assertTrue(show.items() >= 6);
            if (previous != null) {
                assertTrue(show.items() > previous.items());
                assertTrue(show.pitch() < previous.pitch());
                assertNotEquals(previous.pedestalMaterial(), show.pedestalMaterial());
            }
            previous = show;
        }
        assertEquals(KitShow.of(0), KitShow.of(-3));
        assertEquals(KitShow.of(KitShow.MAXIMUM), KitShow.of(99));
    }

    @Test
    void everyLevelRunsADifferentCeremonyThatLastsLonger() {
        List<KitCeremony> ceremonies = new ArrayList<>();
        Set<Class<?>> kinds = new HashSet<>();
        for (int level = 0; level <= KitShow.MAXIMUM; level++) {
            KitShow show = KitShow.of(level);
            KitCeremony ceremony = KitCeremony.of(show);
            assertTrue(kinds.add(ceremony.getClass()), "cérémonie réutilisée au niveau " + level);
            assertTrue(ceremony.duration() > 0);
            assertEquals(ceremony.duration(), show.duration());
            if (!ceremonies.isEmpty()) {
                assertTrue(ceremony.duration() > ceremonies.getLast().duration(), "durée niveau " + level);
            }
            ceremonies.add(ceremony);
        }
        assertEquals(KitShow.MAXIMUM + 1, kinds.size());
        assertTrue(KitCeremony.of(KitShow.of(0)) instanceof KitDropCeremony);
        assertTrue(KitCeremony.of(KitShow.of(1)) instanceof KitBloomCeremony);
        assertTrue(KitCeremony.of(KitShow.of(2)) instanceof KitRitualCeremony);
        assertTrue(KitCeremony.of(KitShow.of(3)) instanceof KitStormCeremony);
        assertTrue(KitCeremony.of(KitShow.of(4)) instanceof KitApotheosisCeremony);
        assertFalse(KitCeremony.of(KitShow.of(4)) instanceof KitStormCeremony);
    }
}
