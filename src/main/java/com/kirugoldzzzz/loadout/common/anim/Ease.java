package com.kirugoldzzzz.loadout.common.anim;

public final class Ease {

    private static final double BACK = 1.70158D;

    private Ease() {
    }

    public static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    public static double progress(int now, int start, int duration) {
        if (duration <= 0) {
            return now >= start ? 1.0D : 0.0D;
        }
        return clamp((now - start) / (double) duration);
    }

    public static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    public static double inCubic(double t) {
        double clamped = clamp(t);
        return clamped * clamped * clamped;
    }

    public static double outCubic(double t) {
        double inverse = 1.0D - clamp(t);
        return 1.0D - inverse * inverse * inverse;
    }

    public static double inOutCubic(double t) {
        double clamped = clamp(t);
        if (clamped < 0.5D) {
            return 4.0D * clamped * clamped * clamped;
        }
        double inverse = -2.0D * clamped + 2.0D;
        return 1.0D - inverse * inverse * inverse / 2.0D;
    }

    public static double outBack(double t) {
        double shifted = clamp(t) - 1.0D;
        return 1.0D + (BACK + 1.0D) * shifted * shifted * shifted + BACK * shifted * shifted;
    }

    public static double approach(double current, double target, double rate) {
        double next = current + (target - current) * rate;
        return Math.abs(target - next) < 0.005D ? target : next;
    }
}
