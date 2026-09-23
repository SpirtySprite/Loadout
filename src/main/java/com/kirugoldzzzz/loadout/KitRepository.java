package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.storage.Database;
import com.kirugoldzzzz.loadout.common.storage.SqlRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KitRepository extends SqlRepository<KitRepository.Key> {

    public enum Kind {
        CLAIM,
        STOCK,
        FAVORITE,
        MILESTONE
    }

    private final Map<UUID, Map<String, KitClaim>> claims = new ConcurrentHashMap<>();
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> favorites = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> milestones = new ConcurrentHashMap<>();

    public KitRepository(Database database) {
        super(database);
    }

    @Override
    protected void schema(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS kit_claims ("
                + "player TEXT NOT NULL,"
                + "kit TEXT NOT NULL,"
                + "uses INTEGER NOT NULL,"
                + "first_at INTEGER NOT NULL,"
                + "last_at INTEGER NOT NULL,"
                + "PRIMARY KEY(player, kit))");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_kit_claims_kit ON kit_claims(kit)");
        addColumn(statement, "tier");
        addColumn(statement, "streak");
        addColumn(statement, "best_streak");
        statement.execute("CREATE TABLE IF NOT EXISTS kit_stock ("
                + "kit TEXT PRIMARY KEY NOT NULL,"
                + "claimed INTEGER NOT NULL)");
        statement.execute("CREATE TABLE IF NOT EXISTS kit_favorites ("
                + "player TEXT NOT NULL,"
                + "kit TEXT NOT NULL,"
                + "PRIMARY KEY(player, kit))");
        statement.execute("CREATE TABLE IF NOT EXISTS kit_milestones ("
                + "player TEXT NOT NULL,"
                + "milestone TEXT NOT NULL,"
                + "PRIMARY KEY(player, milestone))");
    }

    private static void addColumn(Statement statement, String column) throws SQLException {
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(kit_claims)")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) {
                    return;
                }
            }
        }
        statement.execute("ALTER TABLE kit_claims ADD COLUMN " + column + " INTEGER NOT NULL DEFAULT 0");
    }

    @Override
    protected void readAll(Connection connection) throws SQLException {
        claims.clear();
        stock.clear();
        favorites.clear();
        milestones.clear();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT player, kit, uses, first_at, last_at, tier, streak, best_streak FROM kit_claims")) {
            while (results.next()) {
                try {
                    KitClaim claim = new KitClaim(UUID.fromString(results.getString(1)), results.getString(2),
                            results.getInt(3), results.getLong(4), results.getLong(5), results.getInt(6),
                            results.getInt(7), results.getInt(8));
                    claims.computeIfAbsent(claim.player(), ignored -> new ConcurrentHashMap<>())
                            .put(claim.kit(), claim);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT kit, claimed FROM kit_stock")) {
            while (results.next()) {
                stock.put(results.getString(1), Math.max(0, results.getInt(2)));
            }
        }
        readSets(connection, "SELECT player, kit FROM kit_favorites", favorites);
        readSets(connection, "SELECT player, milestone FROM kit_milestones", milestones);
    }

    private static void readSets(Connection connection, String sql, Map<UUID, Set<String>> target) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery(sql)) {
            while (results.next()) {
                try {
                    target.computeIfAbsent(UUID.fromString(results.getString(1)),
                            ignored -> ConcurrentHashMap.newKeySet()).add(results.getString(2));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    @Override
    protected void persistAll(Connection connection, Set<Key> keys) throws SQLException {
        try (PreparedStatement claimStatement = connection.prepareStatement(
                "INSERT INTO kit_claims(player, kit, uses, first_at, last_at, tier, streak, best_streak) "
                        + "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(player, kit) DO UPDATE SET uses=excluded.uses, "
                        + "first_at=excluded.first_at, last_at=excluded.last_at, tier=excluded.tier, "
                        + "streak=excluded.streak, best_streak=excluded.best_streak");
             PreparedStatement stockStatement = connection.prepareStatement(
                     "INSERT INTO kit_stock(kit, claimed) VALUES(?,?) "
                             + "ON CONFLICT(kit) DO UPDATE SET claimed=excluded.claimed");
             PreparedStatement favoriteStatement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO kit_favorites(player, kit) VALUES(?,?)");
             PreparedStatement milestoneStatement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO kit_milestones(player, milestone) VALUES(?,?)")) {
            for (Key key : keys) {
                switch (key.kind()) {
                    case STOCK -> {
                        Integer claimed = stock.get(key.name());
                        if (claimed != null) {
                            stockStatement.setString(1, key.name());
                            stockStatement.setInt(2, claimed);
                            stockStatement.addBatch();
                        }
                    }
                    case CLAIM -> {
                        KitClaim claim = find(key.player(), key.name());
                        if (claim != null) {
                            claimStatement.setString(1, claim.player().toString());
                            claimStatement.setString(2, claim.kit());
                            claimStatement.setInt(3, claim.uses());
                            claimStatement.setLong(4, claim.first());
                            claimStatement.setLong(5, claim.last());
                            claimStatement.setInt(6, claim.tier());
                            claimStatement.setInt(7, claim.streak());
                            claimStatement.setInt(8, claim.best());
                            claimStatement.addBatch();
                        }
                    }
                    case FAVORITE -> {
                        favoriteStatement.setString(1, key.player().toString());
                        favoriteStatement.setString(2, key.name());
                        favoriteStatement.addBatch();
                    }
                    case MILESTONE -> {
                        milestoneStatement.setString(1, key.player().toString());
                        milestoneStatement.setString(2, key.name());
                        milestoneStatement.addBatch();
                    }
                }
            }
            claimStatement.executeBatch();
            stockStatement.executeBatch();
            favoriteStatement.executeBatch();
            milestoneStatement.executeBatch();
        }
    }

    @Override
    protected void eraseAll(Connection connection, Set<Key> keys) throws SQLException {
        try (PreparedStatement claimStatement = connection.prepareStatement(
                "DELETE FROM kit_claims WHERE player=? AND kit=?");
             PreparedStatement stockStatement = connection.prepareStatement("DELETE FROM kit_stock WHERE kit=?");
             PreparedStatement favoriteStatement = connection.prepareStatement(
                     "DELETE FROM kit_favorites WHERE player=? AND kit=?");
             PreparedStatement milestoneStatement = connection.prepareStatement(
                     "DELETE FROM kit_milestones WHERE player=? AND milestone=?")) {
            for (Key key : keys) {
                switch (key.kind()) {
                    case STOCK -> {
                        stockStatement.setString(1, key.name());
                        stockStatement.addBatch();
                    }
                    case CLAIM -> bind(claimStatement, key);
                    case FAVORITE -> bind(favoriteStatement, key);
                    case MILESTONE -> bind(milestoneStatement, key);
                }
            }
            claimStatement.executeBatch();
            stockStatement.executeBatch();
            favoriteStatement.executeBatch();
            milestoneStatement.executeBatch();
        }
    }

    private static void bind(PreparedStatement statement, Key key) throws SQLException {
        statement.setString(1, key.player().toString());
        statement.setString(2, key.name());
        statement.addBatch();
    }

    public KitClaim find(UUID player, String kit) {
        Map<String, KitClaim> owned = player == null ? null : claims.get(player);
        return owned == null || kit == null ? null : owned.get(normalize(kit));
    }

    public Map<String, KitClaim> claimsOf(UUID player) {
        Map<String, KitClaim> owned = claims.get(player);
        return owned == null ? Map.of() : Map.copyOf(owned);
    }

    public int stockUsed(String kit) {
        return stock.getOrDefault(normalize(kit), 0);
    }

    public KitClaim record(UUID player, String kit, long now) {
        return record(player, kit, now, -1);
    }

    public KitClaim record(UUID player, String kit, long now, int streak) {
        String id = normalize(kit);
        return change(() -> {
            Map<String, KitClaim> owned = claims.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
            KitClaim previous = owned.get(id);
            KitClaim updated = previous == null ? KitClaim.first(player, id, now) : previous.again(now);
            if (streak >= 0) {
                updated = updated.withStreak(streak);
            }
            KitClaim stored = updated;
            rollback(() -> restore(owned, id, previous, stored));
            owned.put(id, stored);
            markDirty(new Key(Kind.CLAIM, id, player));
            return stored;
        });
    }

    public KitClaim setTier(UUID player, String kit, int tier) {
        String id = normalize(kit);
        return change(() -> {
            Map<String, KitClaim> owned = claims.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
            KitClaim previous = owned.get(id);
            KitClaim updated = (previous == null ? new KitClaim(player, id, 0, 0L, 0L) : previous).withTier(tier);
            rollback(() -> restore(owned, id, previous, updated));
            owned.put(id, updated);
            markDirty(new Key(Kind.CLAIM, id, player));
            return updated;
        });
    }

    private static void restore(Map<String, KitClaim> owned, String id, KitClaim previous, KitClaim updated) {
        if (previous == null) {
            owned.remove(id, updated);
        } else {
            owned.put(id, previous);
        }
    }

    public int consumeStock(String kit) {
        String id = normalize(kit);
        return change(() -> {
            Integer previous = stock.get(id);
            int updated = previous == null ? 1 : previous + 1;
            rollback(() -> {
                if (previous == null) {
                    stock.remove(id);
                } else {
                    stock.put(id, previous);
                }
            });
            stock.put(id, updated);
            markDirty(new Key(Kind.STOCK, id, null));
            return updated;
        });
    }

    public void resetStock(String kit) {
        String id = normalize(kit);
        change(() -> {
            Integer previous = stock.get(id);
            if (previous != null) {
                rollback(() -> stock.put(id, previous));
                stock.remove(id);
                markRemoved(new Key(Kind.STOCK, id, null));
            }
            return null;
        });
    }

    public int reset(UUID player, String kit) {
        return change(() -> {
            Map<String, KitClaim> owned = claims.get(player);
            if (owned == null) {
                return 0;
            }
            List<KitClaim> removed = new ArrayList<>();
            if (kit == null) {
                removed.addAll(owned.values());
            } else {
                KitClaim single = owned.get(normalize(kit));
                if (single != null) {
                    removed.add(single);
                }
            }
            if (removed.isEmpty()) {
                return 0;
            }
            rollback(() -> removed.forEach(claim -> claims
                    .computeIfAbsent(player, ignored -> new ConcurrentHashMap<>()).put(claim.kit(), claim)));
            for (KitClaim claim : removed) {
                owned.remove(claim.kit(), claim);
                markRemoved(new Key(Kind.CLAIM, claim.kit(), player));
            }
            return removed.size();
        });
    }

    public KitClaim undo(UUID player, String kit) {
        String id = normalize(kit);
        return change(() -> {
            Map<String, KitClaim> owned = claims.get(player);
            KitClaim previous = owned == null ? null : owned.get(id);
            if (previous == null) {
                return null;
            }
            rollback(() -> claims.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>()).put(id, previous));
            if (previous.uses() <= 1 && previous.tier() <= 0) {
                owned.remove(id, previous);
                markRemoved(new Key(Kind.CLAIM, id, player));
                return null;
            }
            KitClaim updated = previous.withUses(Math.max(0, previous.uses() - 1), 0L);
            owned.put(id, updated);
            markDirty(new Key(Kind.CLAIM, id, player));
            return updated;
        });
    }

    public int forget(String kit) {
        String id = normalize(kit);
        return change(() -> {
            int removed = 0;
            for (Map.Entry<UUID, Map<String, KitClaim>> entry : claims.entrySet()) {
                Map<String, KitClaim> owned = entry.getValue();
                KitClaim claim = owned.get(id);
                if (claim != null) {
                    UUID player = entry.getKey();
                    rollback(() -> owned.put(id, claim));
                    owned.remove(id);
                    markRemoved(new Key(Kind.CLAIM, id, player));
                    removed++;
                }
            }
            for (Map.Entry<UUID, Set<String>> entry : favorites.entrySet()) {
                Set<String> set = entry.getValue();
                if (set.contains(id)) {
                    rollback(() -> set.add(id));
                    set.remove(id);
                    markRemoved(new Key(Kind.FAVORITE, id, entry.getKey()));
                }
            }
            Integer previous = stock.get(id);
            if (previous != null) {
                rollback(() -> stock.put(id, previous));
                stock.remove(id);
                markRemoved(new Key(Kind.STOCK, id, null));
            }
            return removed;
        });
    }

    public Set<String> favorites(UUID player) {
        Set<String> set = favorites.get(player);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public boolean toggleFavorite(UUID player, String kit) {
        String id = normalize(kit);
        return change(() -> {
            Set<String> set = favorites.computeIfAbsent(player, ignored -> ConcurrentHashMap.newKeySet());
            boolean had = set.contains(id);
            rollback(() -> {
                if (had) {
                    set.add(id);
                } else {
                    set.remove(id);
                }
            });
            if (had) {
                set.remove(id);
                markRemoved(new Key(Kind.FAVORITE, id, player));
            } else {
                set.add(id);
                markDirty(new Key(Kind.FAVORITE, id, player));
            }
            return !had;
        });
    }

    public Set<String> milestones(UUID player) {
        Set<String> set = milestones.get(player);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public boolean grantMilestone(UUID player, String milestone) {
        return change(() -> {
            Set<String> set = milestones.computeIfAbsent(player, ignored -> ConcurrentHashMap.newKeySet());
            if (set.contains(milestone)) {
                return false;
            }
            rollback(() -> set.remove(milestone));
            set.add(milestone);
            markDirty(new Key(Kind.MILESTONE, milestone, player));
            return true;
        });
    }

    public int resetMilestones(UUID player) {
        return change(() -> {
            Set<String> set = milestones.get(player);
            if (set == null || set.isEmpty()) {
                return 0;
            }
            List<String> removed = List.copyOf(set);
            rollback(() -> set.addAll(removed));
            for (String milestone : removed) {
                set.remove(milestone);
                markRemoved(new Key(Kind.MILESTONE, milestone, player));
            }
            return removed.size();
        });
    }

    public int distinct(UUID player) {
        Map<String, KitClaim> owned = claims.get(player);
        if (owned == null) {
            return 0;
        }
        int count = 0;
        for (KitClaim claim : owned.values()) {
            if (claim.uses() > 0) {
                count++;
            }
        }
        return count;
    }

    public Stats stats(String kit) {
        String id = normalize(kit);
        long total = 0L;
        int players = 0;
        long last = 0L;
        int bestStreak = 0;
        for (Map<String, KitClaim> owned : claims.values()) {
            KitClaim claim = owned.get(id);
            if (claim != null && claim.uses() > 0) {
                players++;
                total += claim.uses();
                last = Math.max(last, claim.last());
                bestStreak = Math.max(bestStreak, claim.best());
            }
        }
        return new Stats(total, players, last, stockUsed(id), bestStreak);
    }

    public int players() {
        return claims.size();
    }

    public long totalClaims() {
        long total = 0L;
        for (Map<String, KitClaim> owned : claims.values()) {
            for (KitClaim claim : owned.values()) {
                total += claim.uses();
            }
        }
        return total;
    }

    void watchInventory(Player player) {
        ItemStack[] previous = Arrays.stream(player.getInventory().getContents())
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        rollback(() -> player.getInventory().setContents(previous));
    }

    void watch(Runnable undo) {
        rollback(undo);
    }

    static String normalize(String kit) {
        return kit == null ? "" : kit.trim().toLowerCase(Locale.ROOT);
    }

    public record Key(Kind kind, String name, UUID player) {
    }

    public record Stats(long claims, int players, long last, int stockUsed, int bestStreak) {
    }
}
