package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.storage.Database;
import com.kirugoldzzzz.loadout.support.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KitRepositoryTest {

    @TempDir
    Path folder;

    private Database database;
    private KitRepository repository;

    private final UUID alice = new UUID(0L, 1L);
    private final UUID bob = new UUID(0L, 2L);

    @BeforeEach
    void open() throws SQLException {
        database = new Database(TestPlugin.at(folder.toFile()), "kits.db");
        database.open();
        repository = new KitRepository(database);
        repository.load();
    }

    @AfterEach
    void close() {
        database.close();
    }

    private KitRepository reopened() {
        repository.flush();
        KitRepository fresh = new KitRepository(database);
        fresh.load();
        return fresh;
    }

    @Test
    void claimsAndStockSurviveARestart() {
        repository.record(alice, "Starter", 1_000L);
        repository.record(alice, "starter", 5_000L);
        repository.record(bob, "starter", 2_000L);
        repository.consumeStock("starter");
        repository.consumeStock("starter");

        KitRepository fresh = reopened();
        KitClaim claim = fresh.find(alice, "STARTER");
        assertNotNull(claim);
        assertEquals(2, claim.uses());
        assertEquals(1_000L, claim.first());
        assertEquals(5_000L, claim.last());
        assertEquals(2, fresh.stockUsed("starter"));
        KitRepository.Stats stats = fresh.stats("starter");
        assertEquals(3L, stats.claims());
        assertEquals(2, stats.players());
        assertEquals(5_000L, stats.last());
        assertEquals(3L, fresh.totalClaims());
    }

    @Test
    void resetsUndoAndForgetAreDurable() {
        repository.record(alice, "starter", 1_000L);
        repository.record(alice, "daily", 1_000L);
        repository.record(alice, "daily", 2_000L);
        repository.record(bob, "daily", 3_000L);
        repository.consumeStock("daily");

        KitClaim undone = repository.undo(alice, "daily");
        assertEquals(1, undone.uses());
        assertEquals(0L, undone.last());
        assertEquals(1, repository.reset(alice, "starter"));
        assertEquals(2, repository.forget("daily"));

        KitRepository fresh = reopened();
        assertNull(fresh.find(alice, "starter"));
        assertNull(fresh.find(alice, "daily"));
        assertNull(fresh.find(bob, "daily"));
        assertEquals(0, fresh.stockUsed("daily"));
    }

    @Test
    void aFailedTransactionLeavesNothingBehind() {
        repository.record(alice, "starter", 1_000L);
        assertThrows(IllegalStateException.class, () -> repository.change(() -> {
            repository.record(alice, "starter", 9_000L);
            repository.consumeStock("starter");
            repository.reset(bob, null);
            throw new IllegalStateException("échec simulé");
        }));
        assertEquals(1, repository.find(alice, "starter").uses());
        assertEquals(0, repository.stockUsed("starter"));

        KitRepository fresh = reopened();
        assertEquals(1, fresh.find(alice, "starter").uses());
        assertEquals(1_000L, fresh.find(alice, "starter").last());
        assertEquals(0, fresh.stockUsed("starter"));
    }
}
