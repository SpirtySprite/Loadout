package com.kirugoldzzzz.loadout.common.storage;

import com.kirugoldzzzz.loadout.common.item.ItemCodec;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RecoveryRepository extends SqlRepository<UUID> {

    private final Map<UUID, List<ItemStack>> pending = new ConcurrentHashMap<>();

    public RecoveryRepository(Database database) {
        super(database);
    }

    @Override
    protected void schema(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS recovery ("
                + "owner TEXT PRIMARY KEY NOT NULL,"
                + "items TEXT NOT NULL)");
        statement.execute("CREATE TABLE IF NOT EXISTS recovery_unreadable ("
                + "owner TEXT NOT NULL,"
                + "items TEXT NOT NULL,"
                + "found_at INTEGER NOT NULL)");
    }

    @Override
    protected void readAll(Connection connection) throws SQLException {
        pending.clear();
        List<String[]> unreadable = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT owner, items FROM recovery")) {
            while (results.next()) {
                try {
                    List<ItemStack> items = ItemCodec.decodeAllRequired(results.getString(2));
                    if (!items.isEmpty()) {
                        pending.put(UUID.fromString(results.getString(1)), List.copyOf(items));
                    }
                } catch (IllegalArgumentException | IllegalStateException failure) {
                    unreadable.add(new String[]{results.getString(1), results.getString(2)});
                }
            }
        }
        if (unreadable.isEmpty()) {
            return;
        }
        try (PreparedStatement keep = connection.prepareStatement(
                "INSERT INTO recovery_unreadable(owner, items, found_at) VALUES(?,?,?)");
             PreparedStatement drop = connection.prepareStatement("DELETE FROM recovery WHERE owner=?")) {
            long now = System.currentTimeMillis();
            for (String[] row : unreadable) {
                keep.setString(1, row[0]);
                keep.setString(2, row[1]);
                keep.setLong(3, now);
                keep.addBatch();
                drop.setString(1, row[0]);
                drop.addBatch();
            }
            keep.executeBatch();
            drop.executeBatch();
        }
    }

    @Override
    protected void persistAll(Connection connection, Set<UUID> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO recovery(owner, items) VALUES(?,?) "
                        + "ON CONFLICT(owner) DO UPDATE SET items=excluded.items")) {
            for (UUID key : keys) {
                List<ItemStack> items = pending.get(key);
                if (items == null || items.isEmpty()) {
                    continue;
                }
                statement.setString(1, key.toString());
                statement.setString(2, ItemCodec.encodeAll(items));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    protected void eraseAll(Connection connection, Set<UUID> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM recovery WHERE owner=?")) {
            for (UUID key : keys) {
                statement.setString(1, key.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public void store(UUID owner, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        List<ItemStack> added = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                added.add(item.clone());
            }
        }
        if (added.isEmpty()) {
            return;
        }
        pending.compute(owner, (id, existing) -> {
            List<ItemStack> merged = existing == null ? new ArrayList<>(added.size()) : new ArrayList<>(existing);
            merged.addAll(added);
            return List.copyOf(merged);
        });
        markDirty(owner);
    }

    public List<ItemStack> take(UUID owner) {
        List<ItemStack> items = pending.remove(owner);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        markRemoved(owner);
        return items;
    }

    public int count(UUID owner) {
        List<ItemStack> items = pending.get(owner);
        if (items == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : items) {
            total += item.getAmount();
        }
        return total;
    }

    public int owners() {
        return pending.size();
    }

    public boolean has(UUID owner) {
        List<ItemStack> items = pending.get(owner);
        return items != null && !items.isEmpty();
    }
}
