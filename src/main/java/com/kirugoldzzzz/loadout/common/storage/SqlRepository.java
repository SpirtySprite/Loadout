package com.kirugoldzzzz.loadout.common.storage;

import com.kirugoldzzzz.loadout.common.diag.Diagnostics;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public abstract class SqlRepository<K> implements Store {

    protected final Database database;

    private final Map<K, Change> pending = new ConcurrentHashMap<>();

    private record Change(boolean removed) {}

    final Runnable pendingRollback() {
        Map<K, Change> previous = Map.copyOf(pending);
        return () -> {
            pending.clear();
            pending.putAll(previous);
        };
    }

    protected SqlRepository(Database database) {
        this.database = database;
    }

    protected abstract void schema(Statement statement) throws SQLException;

    protected abstract void readAll(Connection connection) throws SQLException;

    protected abstract void persistAll(Connection connection, Set<K> keys) throws SQLException;

    protected abstract void eraseAll(Connection connection, Set<K> keys) throws SQLException;

    @Override
    public final void load() {
        database.exclusive(() -> {
            loadLocked();
            return null;
        });
    }

    private void loadLocked() {
        long start = System.nanoTime();
        boolean loaded = database.transaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                schema(statement);
            }
            readAll(connection);
        });
        if (loaded) {
            pending.clear();
        }
        Diagnostics.record(Diagnostics.STORAGE_LOAD, System.nanoTime() - start);
        if (!loaded) {
            throw new IllegalStateException("Chargement impossible: " + getClass().getSimpleName());
        }
    }

    public final <T> T change(Supplier<T> work) {
        return database.change(work);
    }

    public final <T> T exclusive(Supplier<T> work) {
        return database.exclusive(work);
    }

    public final void afterCommit(Runnable action) {
        database.afterCommit(action);
    }

    protected final void rollback(Runnable undo) {
        database.enlist(this, undo);
    }

    public final int pending() {
        return pending.size();
    }

    public final void markDirty(K key) {
        pending.put(key, new Change(false));
    }

    public final void markRemoved(K key) {
        pending.put(key, new Change(true));
    }

    @Override
    public final void flush() {
        database.exclusive(() -> {
            if (pending.isEmpty()) {
                return null;
            }
            Batch batch = batch();
            if (database.transaction(batch::write)) {
                batch.complete();
            }
            return null;
        });
    }

    final Batch batch() {
        Map<K, Change> captured = Map.copyOf(pending);
        Set<K> writes = new HashSet<>();
        Set<K> deletes = new HashSet<>();
        captured.forEach((key, change) -> (change.removed() ? deletes : writes).add(key));
        return new Batch() {
            public void write(Connection connection) throws SQLException {
                if (!deletes.isEmpty()) {
                    eraseAll(connection, deletes);
                }
                if (!writes.isEmpty()) {
                    persistAll(connection, writes);
                }
            }

            public void complete() {
                captured.forEach((key, value) -> pending.computeIfPresent(key,
                        (ignored, current) -> current == value ? null : current));
            }
        };
    }

    interface Batch {
        void write(Connection connection) throws SQLException;
        void complete();
    }

    @Override
    public final void flushNow() {
        flush();
    }
}
