package com.kirugoldzzzz.loadout.support;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class Timings {

    private Timings() {
    }

    public static boolean sharedRunner() {
        String flag = System.getenv("CI");
        return flag != null && !flag.isBlank() && !"false".equalsIgnoreCase(flag);
    }

    public static void under(String label, double measured, double budget, String unit) {
        System.out.printf(Locale.US, "[perf] %-34s %10.4f %s  (budget %.4f)%s%n",
                label, measured, unit, budget,
                sharedRunner() ? "  [machine partagée, non bloquant]" : "");
        if (sharedRunner()) {
            return;
        }
        assertTrue(measured < budget,
                label + " trop lent: " + measured + " " + unit + ", budget " + budget);
    }
}
