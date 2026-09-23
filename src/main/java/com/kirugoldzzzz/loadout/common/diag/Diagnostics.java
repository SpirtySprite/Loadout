package com.kirugoldzzzz.loadout.common.diag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class Diagnostics {

    public static final String STORAGE_FLUSH = "Sauvegarde périodique";
    public static final String STORAGE_LOAD = "Chargement au démarrage";
    public static final String BACKUP_CAPTURE = "Capture de sauvegarde";
    public static final String BACKUP_RESTORE = "Restauration";
    public static final String ORDER_PURGE = "Purge des ordres expirés";
    public static final String RTP_SEARCH = "Recherche de position sûre";
    public static final String MENU_OPEN = "Ouverture de menu";

    private static final int WARMUP = 3;

    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();

    private static volatile long startedAt = System.currentTimeMillis();

    private Diagnostics() {
    }

    public static void markStart() {
        startedAt = System.currentTimeMillis();
        COUNTERS.clear();
    }

    public static long uptimeMillis() {
        return System.currentTimeMillis() - startedAt;
    }

    public static void record(String key, long nanos) {
        COUNTERS.computeIfAbsent(key, name -> new Counter()).add(nanos);
    }

    public static void time(String key, Runnable work) {
        long start = System.nanoTime();
        try {
            work.run();
        } finally {
            record(key, System.nanoTime() - start);
        }
    }

    public static <T> T time(String key, Supplier<T> work) {
        long start = System.nanoTime();
        try {
            return work.get();
        } finally {
            record(key, System.nanoTime() - start);
        }
    }

    public static Sample sample(String key) {
        Counter counter = COUNTERS.get(key);
        return counter == null ? Sample.empty(key) : counter.snapshot(key);
    }

    public static List<Sample> samples() {
        List<Sample> samples = new ArrayList<>(COUNTERS.size());
        COUNTERS.forEach((key, counter) -> samples.add(counter.snapshot(key)));
        samples.sort(Comparator.comparingDouble(Sample::maxMillis).reversed());
        return samples;
    }

    public static void reset() {
        COUNTERS.clear();
    }

    private static final class Counter {

        private long count;
        private long total;
        private long max;
        private long last;
        private long warmCount;
        private long warmTotal;
        private long warmMax;

        synchronized void add(long nanos) {
            count++;
            total += nanos;
            last = nanos;
            if (nanos > max) {
                max = nanos;
            }
            if (count > WARMUP) {
                warmCount++;
                warmTotal += nanos;
                if (nanos > warmMax) {
                    warmMax = nanos;
                }
            }
        }

        synchronized Sample snapshot(String key) {
            return new Sample(key, count, total, max, last, warmCount, warmTotal, warmMax);
        }
    }

    public record Sample(String key, long count, long totalNanos, long maxNanos, long lastNanos,
                         long warmCount, long warmTotalNanos, long warmMaxNanos) {

        static Sample empty(String key) {
            return new Sample(key, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        public boolean isEmpty() {
            return count == 0L;
        }

        public boolean warmedUp() {
            return warmCount > 0L;
        }

        public long warmupSamples() {
            return count - warmCount;
        }

        public double averageMillis() {
            long samples = warmedUp() ? warmCount : count;
            long sum = warmedUp() ? warmTotalNanos : totalNanos;
            return samples == 0L ? 0.0D : sum / (double) samples / 1_000_000.0D;
        }

        public double maxMillis() {
            return (warmedUp() ? warmMaxNanos : maxNanos) / 1_000_000.0D;
        }

        public double coldMaxMillis() {
            return maxNanos / 1_000_000.0D;
        }

        public double lastMillis() {
            return lastNanos / 1_000_000.0D;
        }
    }
}
