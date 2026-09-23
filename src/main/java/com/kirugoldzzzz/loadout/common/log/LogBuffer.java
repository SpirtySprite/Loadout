package com.kirugoldzzzz.loadout.common.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class LogBuffer {

    public static final int CAPACITY = 1000;

    public enum Severity {
        DEBUG,
        INFO,
        WARNING,
        ERROR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Severity of(Level level) {
            int value = level.intValue();
            if (value >= Level.SEVERE.intValue()) {
                return ERROR;
            }
            if (value >= Level.WARNING.intValue()) {
                return WARNING;
            }
            return value >= Level.INFO.intValue() ? INFO : DEBUG;
        }
    }

    public record Entry(long id, long at, Severity severity, LogTopic topic, String message, String failure) {
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static final List<Consumer<Entry>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private LogBuffer() {
    }

    static void record(Level level, LogTopic topic, String message, Throwable failure) {
        Entry entry = new Entry(SEQUENCE.incrementAndGet(), System.currentTimeMillis(), Severity.of(level),
                topic == null ? LogTopic.GENERAL : topic, message == null ? "" : message,
                failure == null ? null : failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : " : " + failure.getMessage()));
        synchronized (ENTRIES) {
            ENTRIES.addLast(entry);
            while (ENTRIES.size() > CAPACITY) {
                ENTRIES.pollFirst();
            }
        }
        for (Consumer<Entry> listener : LISTENERS) {
            try {
                listener.accept(entry);
            } catch (RuntimeException ignored) {
                continue;
            }
        }
    }

    public static List<Entry> recent() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES);
        }
    }

    public static Runnable listen(Consumer<Entry> listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }
}
