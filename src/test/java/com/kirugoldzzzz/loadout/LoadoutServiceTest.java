package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.api.LoadoutApi;
import com.kirugoldzzzz.loadout.common.storage.Database;
import com.kirugoldzzzz.loadout.support.TestPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutServiceTest {

    private static final UUID PLAYER = new UUID(0L, 7L);

    @TempDir
    Path folder;

    private Database database;
    private KitRepository repository;
    private LoadoutApi api;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(TestPlugin.at(folder.toFile()), "api.db");
        database.open();
        repository = new KitRepository(database);
        repository.load();
        KitService service = new KitService(repository, null, new CrateBridge(folder.toFile()),
                new PlayerSettingsRepository(new File(folder.toFile(), "preferences.yml")));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("kits.starter.name", "Starter");
        yaml.set("kits.starter.rewards.money", 10);
        service.configure(yaml);
        api = new LoadoutService(service, null, null);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void listsConfiguredKits() {
        assertEquals(List.of("starter"), api.kits());
        assertTrue(api.exists("starter"));
        assertFalse(api.exists("ghost"));
    }

    @Test
    void countsUsesAndResetsThem() {
        assertEquals(0, api.uses(PLAYER, "starter"));
        repository.record(PLAYER, "starter", System.currentTimeMillis());
        assertEquals(1, api.uses(PLAYER, "starter"));
        assertEquals(1, api.reset(PLAYER, "starter"));
        assertEquals(0, api.uses(PLAYER, "starter"));
    }

    @Test
    void unknownKitsAndBadAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> api.uses(PLAYER, "ghost"));
        assertThrows(IllegalArgumentException.class, () -> api.reset(PLAYER, "ghost"));
        assertThrows(IllegalArgumentException.class, () -> api.giveVouchers(null, "starter", 0));
    }
}
