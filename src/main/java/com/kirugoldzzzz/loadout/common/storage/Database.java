package com.kirugoldzzzz.loadout.common.storage;

import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.NexusLog;
import com.kirugoldzzzz.loadout.common.log.StaffAlert;
import com.kirugoldzzzz.loadout.common.text.Card;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.Map;

public final class Database {

    private static final long TRANSACTION_ALERT_COOLDOWN_MILLIS = 60_000L;
    private static final int AUTOCHECKPOINT_PAGES = 8_000;

    private final File file;
    private final Object lock = new Object();

    private Connection connection;
    private volatile boolean opened;
    private final Object readLock = new Object();
    private Connection reader;
    private List<Runnable> undo;
    private Set<SqlRepository<?>> changed;
    private List<Runnable> committed;

    public <T> T exclusive(Supplier<T> work) {
        synchronized (lock) {
            return work.get();
        }
    }

    public <T> T change(Supplier<T> work) {
        synchronized (lock) {
            if (undo != null) {
                return work.get();
            }
            undo = new ArrayList<>();
            changed = new LinkedHashSet<>();
            committed = new ArrayList<>();
            T result;
            List<Runnable> notifications;
            try {
                result = work.get();
                List<SqlRepository.Batch> batches = changed.stream()
                        .map(SqlRepository::batch).toList();
                if (!batches.isEmpty() && !transaction(connection -> {
                    for (SqlRepository.Batch batch : batches) {
                        batch.write(connection);
                    }
                })) {
                    throw new IllegalStateException("Écriture impossible, opération annulée");
                }
                batches.forEach(SqlRepository.Batch::complete);
                notifications = List.copyOf(committed);
            } catch (RuntimeException | Error failure) {
                for (int index = undo.size() - 1; index >= 0; index--) {
                    try {
                        undo.get(index).run();
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            } finally {
                undo = null;
                changed = null;
                committed = null;
            }
            for (Runnable notification : notifications) {
                try {
                    notification.run();
                } catch (RuntimeException failure) {
                    NexusLog.warn(LogTopic.STORAGE, "Notification impossible après validation", failure);
                }
            }
            return result;
        }
    }

    public void afterCommit(Runnable action) {
        if (!Thread.holdsLock(lock) || committed == null) {
            throw new IllegalStateException("Notification hors transaction");
        }
        committed.add(action);
    }

    public void enlist(SqlRepository<?> repository, Runnable rollback) {
        if (!Thread.holdsLock(lock) || undo == null) {
            throw new IllegalStateException("Modification hors transaction");
        }
        if (changed.add(repository)) {
            undo.add(repository.pendingRollback());
        }
        undo.add(rollback);
    }

    public Database(Plugin plugin, String fileName) {
        this.file = new File(plugin.getDataFolder(), fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            NexusLog.warn(LogTopic.STORAGE, "Impossible de créer le dossier de données " + parent.getPath());
        }
    }

    public void open() throws SQLException {
        synchronized (lock) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=FULL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA temp_store=MEMORY");
                statement.execute("PRAGMA cache_size=-16000");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA wal_autocheckpoint=" + AUTOCHECKPOINT_PAGES);
                statement.execute("CREATE TABLE IF NOT EXISTS schema_versions ("
                        + "component TEXT PRIMARY KEY NOT NULL, version INTEGER NOT NULL)");
            }
            verifyIntegrity();
            verifyForeignKeys();
            opened = true;
        }
    }

    public void close() {
        synchronized (lock) {
            opened = false;
            synchronized (readLock) {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (SQLException ignored) {
                    }
                    reader = null;
                }
            }
            if (connection == null) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA optimize");
            } catch (SQLException ignored) {
            }
            try {
                connection.close();
            } catch (SQLException exception) {
                NexusLog.warn(LogTopic.STORAGE, "Fermeture de la base de données impossible", exception);
            }
            connection = null;
        }
    }

    public long sizeOnDisk() {
        long total = file.length();
        File wal = new File(file.getParentFile(), file.getName() + "-wal");
        return wal.exists() ? total + wal.length() : total;
    }

    public boolean transaction(Work work) {
        synchronized (lock) {
            if (connection == null) {
                return false;
            }
            try {
                connection.setAutoCommit(false);
                work.run(connection);
                connection.commit();
                return true;
            } catch (Throwable failure) {
                rollback();
                StaffAlert.critical(LogTopic.STORAGE, "Écriture en base annulée")
                        .summary("Transaction annulée, aucune modification appliquée")
                        .detail(Card.SEARCH, "Base", file.getName())
                        .error(failure)
                        .throttle("stockage:transaction", TRANSACTION_ALERT_COOLDOWN_MILLIS)
                        .send();
                if (failure instanceof Error error) {
                    throw error;
                }
                return false;
            } finally {
                restoreAutoCommit();
            }
        }
    }

    public boolean read(Work work) {
        synchronized (readLock) {
            if (!opened) {
                return false;
            }
            try {
                if (reader == null || reader.isClosed()) {
                    reader = DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
                    try (Statement statement = reader.createStatement()) {
                        statement.execute("PRAGMA busy_timeout=5000");
                        statement.execute("PRAGMA query_only=1");
                    }
                    reader.setAutoCommit(false);
                }
                try {
                    work.run(reader);
                } finally {
                    reader.rollback();
                }
                return true;
            } catch (Throwable failure) {
                NexusLog.warn(LogTopic.STORAGE, "Lecture en base impossible", failure);
                if (failure instanceof Error error) {
                    throw error;
                }
                return false;
            }
        }
    }

    public int deleteInChunks(String table, String condition, java.util.function.Consumer<PreparedStatement> binder,
                              int chunk) {
        int removed = 0;
        while (true) {
            int[] batch = new int[1];
            boolean done = transaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE rowid IN (SELECT rowid FROM " + table + " WHERE "
                                + condition + " LIMIT " + Math.max(1, chunk) + ")")) {
                    binder.accept(statement);
                    batch[0] = statement.executeUpdate();
                }
            });
            if (!done) {
                return removed;
            }
            removed += batch[0];
            if (batch[0] < chunk) {
                return removed;
            }
        }
    }

    public boolean execute(String sql) {
        synchronized (lock) {
            if (connection == null) {
                return false;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
                return true;
            } catch (SQLException exception) {
                NexusLog.warn(LogTopic.STORAGE, "Instruction refusée : " + sql, exception);
                return false;
            }
        }
    }

    public boolean checkpoint() {
        if (!opened) {
            return false;
        }
        try (Connection side = DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
             Statement statement = side.createStatement()) {
            statement.execute("PRAGMA busy_timeout=2000");
            try (ResultSet result = statement.executeQuery("PRAGMA wal_checkpoint(PASSIVE)")) {
                return result.next() && result.getInt(1) == 0;
            }
        } catch (SQLException exception) {
            NexusLog.warn(LogTopic.STORAGE, "Point de contrôle du journal impossible", exception);
            return false;
        }
    }

    public boolean copyTo(Path target) {
        try (Connection reader = DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
             Statement statement = reader.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
            return true;
        } catch (SQLException exception) {
            NexusLog.warn(LogTopic.STORAGE, "Copie de la base impossible vers " + target, exception);
            return false;
        }
    }

    public void migrate(String component, Map<Integer, String> migrations) {
        boolean migrated = transaction(connection -> {
            int current = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT version FROM schema_versions WHERE component=?")) {
                statement.setString(1, component);
                try (ResultSet results = statement.executeQuery()) {
                    current = results.next() ? results.getInt(1) : 0;
                }
            }
            for (Map.Entry<Integer, String> migration : new java.util.TreeMap<>(migrations).entrySet()) {
                if (migration.getKey() <= current) {
                    continue;
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute(migration.getValue());
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO schema_versions(component, version) VALUES(?,?) "
                                + "ON CONFLICT(component) DO UPDATE SET version=excluded.version")) {
                    statement.setString(1, component);
                    statement.setInt(2, migration.getKey());
                    statement.executeUpdate();
                }
            }
        });
        if (!migrated) {
            throw new IllegalStateException("Migration impossible: " + component);
        }
    }

    public Health health() {
        String[] integrity = {"inconnu"};
        int[] foreignKeys = {-1};
        int[] schema = {0};
        long started = System.nanoTime();
        boolean checked = read(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("PRAGMA quick_check")) {
                integrity[0] = results.next() ? results.getString(1) : "inconnu";
            }
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("PRAGMA foreign_key_check")) {
                while (results.next()) {
                    foreignKeys[0]++;
                }
                foreignKeys[0]++;
            }
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("SELECT COALESCE(SUM(version),0) FROM schema_versions")) {
                schema[0] = results.next() ? results.getInt(1) : 0;
            }
        });
        return new Health(checked && "ok".equalsIgnoreCase(integrity[0]) && foreignKeys[0] == 0,
                integrity[0], foreignKeys[0], schema[0], System.nanoTime() - started);
    }

    public record Health(boolean healthy, String integrity, int foreignKeyErrors,
                         int schemaVersion, long durationNanos) {
    }

    private void verifyIntegrity() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("PRAGMA quick_check(1)")) {
            String verdict = results.next() ? results.getString(1) : "inconnu";
            if (!"ok".equalsIgnoreCase(verdict)) {
                throw new SQLException("Base de données corrompue: " + verdict);
            }
        }
    }

    private void verifyForeignKeys() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("PRAGMA foreign_keys")) {
            if (results.next() && results.getInt(1) != 1) {
                throw new SQLException("Les clés étrangères ne sont pas actives");
            }
        }
    }

    private void rollback() {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            NexusLog.error(LogTopic.STORAGE, "Retour arrière impossible", exception);
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException exception) {
            NexusLog.warn(LogTopic.STORAGE, "Mode auto-commit non restauré", exception);
        }
    }

    @FunctionalInterface
    public interface Work {

        void run(Connection connection) throws SQLException;
    }
}
