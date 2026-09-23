package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.storage.Database;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KitJournal {

    private static volatile KitJournal active;

    private final Database database;

    public KitJournal(Database database) {
        this.database = database;
        database.transaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS kit_history (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "at INTEGER NOT NULL, action TEXT NOT NULL, actor TEXT, actor_name TEXT, subject TEXT, "
                        + "subject_name TEXT, amount REAL NOT NULL, detail TEXT NOT NULL)");
                statement.execute("CREATE INDEX IF NOT EXISTS kit_history_actor ON kit_history (actor, at)");
                statement.execute("CREATE INDEX IF NOT EXISTS kit_history_subject ON kit_history (subject, at)");
            }
        });
    }

    public static void bind(KitJournal journal) {
        active = journal;
    }

    public static void self(String action, Player player, double amount, String detail) {
        record(action, player.getUniqueId(), player.getName(), player.getUniqueId(), player.getName(), amount, detail);
    }

    public static void between(String action, Player actor, UUID subject, String subjectName, double amount,
                               String detail) {
        record(action, actor.getUniqueId(), actor.getName(), subject, subjectName, amount, detail);
    }

    public static void byConsole(String action, UUID subject, String subjectName, double amount, String detail) {
        record(action, null, Tr.t("Console"), subject, subjectName, amount, detail);
    }

    private static void record(String action, UUID actor, String actorName, UUID subject, String subjectName,
                               double amount, String detail) {
        KitJournal journal = active;
        if (journal == null) {
            return;
        }
        Entry entry = new Entry(System.currentTimeMillis(), action, actor, actorName, subject, subjectName, amount,
                detail == null ? "" : detail);
        Scheduling.async(() -> journal.insert(entry));
    }

    private void insert(Entry entry) {
        database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO kit_history "
                    + "(at, action, actor, actor_name, subject, subject_name, amount, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setLong(1, entry.at());
                insert.setString(2, entry.action());
                insert.setString(3, entry.actor() == null ? null : entry.actor().toString());
                insert.setString(4, entry.actorName());
                insert.setString(5, entry.subject() == null ? null : entry.subject().toString());
                insert.setString(6, entry.subjectName());
                insert.setDouble(7, entry.amount());
                insert.setString(8, entry.detail());
                insert.executeUpdate();
            }
        });
    }

    public List<Entry> page(UUID player, int limit, int offset) {
        List<Entry> entries = new ArrayList<>();
        database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement("SELECT at, action, actor, actor_name, subject, "
                    + "subject_name, amount, detail FROM kit_history WHERE actor = ? OR subject = ? "
                    + "ORDER BY at DESC, id DESC LIMIT ? OFFSET ?")) {
                query.setString(1, player.toString());
                query.setString(2, player.toString());
                query.setInt(3, Math.max(1, limit));
                query.setInt(4, Math.max(0, offset));
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        entries.add(new Entry(rows.getLong(1), rows.getString(2), uuid(rows.getString(3)),
                                rows.getString(4), uuid(rows.getString(5)), rows.getString(6), rows.getDouble(7),
                                rows.getString(8)));
                    }
                }
            }
        });
        return entries;
    }

    private static UUID uuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public record Entry(long at, String action, UUID actor, String actorName, UUID subject, String subjectName,
                        double amount, String detail) {

        private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

        public String formattedDate() {
            return STAMP.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));
        }

        public long age() {
            return Math.max(0L, System.currentTimeMillis() - at);
        }

        public boolean hasAmount() {
            return amount != 0.0D;
        }

        public boolean involvesTwoParties() {
            return actor != null && subject != null && !actor.equals(subject);
        }
    }
}
