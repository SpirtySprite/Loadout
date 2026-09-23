package com.kirugoldzzzz.loadout.common.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void newerComparesEachNumberInOrder() {
        assertTrue(UpdateChecker.newer("1.0.1", "1.0.0"));
        assertTrue(UpdateChecker.newer("1.10.0", "1.9.9"));
        assertTrue(UpdateChecker.newer("2.0", "1.9.9"));
        assertTrue(UpdateChecker.newer("1.0.0.1", "1.0.0"));
    }

    @Test
    void sameOrOlderIsNotAnUpdate() {
        assertFalse(UpdateChecker.newer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.newer("1.0", "1.0.0"));
        assertFalse(UpdateChecker.newer("0.9.9", "1.0.0"));
        assertFalse(UpdateChecker.newer("1.0.0-SNAPSHOT", "1.0.0"));
    }

    @Test
    void suffixesAreIgnored() {
        assertTrue(UpdateChecker.newer("1.2.0-beta", "1.1.0-SNAPSHOT"));
        assertFalse(UpdateChecker.newer("garbage", "1.0.0"));
    }
}
