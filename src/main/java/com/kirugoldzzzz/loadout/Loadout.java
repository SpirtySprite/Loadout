package com.kirugoldzzzz.loadout;

import com.foliagui.FoliaGUI;
import com.kirugoldzzzz.loadout.api.LoadoutApi;
import com.kirugoldzzzz.loadout.common.command.CommandBase;
import com.kirugoldzzzz.loadout.common.config.ConfigFile;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.platform.Telemetry;
import com.kirugoldzzzz.loadout.common.platform.UpdateChecker;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.storage.Database;
import com.kirugoldzzzz.loadout.common.storage.StorageManager;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class Loadout extends JavaPlugin {

    private static final long SAVE_INTERVAL_SECONDS = 30L;
    private static final int BSTATS_ID = 0;
    private static final String REPOSITORY = "SpirtySprite/Loadout";

    private final StorageManager storage = new StorageManager();
    private Database database;
    private KitService service;
    private final Telemetry telemetry = new Telemetry();

    @Override
    public void onEnable() {
        Scheduling.bind(this);
        ConfigFile settings = loadSettings();
        FoliaGUI.init(this);
        Guis.installTheme();
        Tr.seedLocalized(this, "kits.yml");
        ConfigFile kits = new ConfigFile(this, "kits.yml", "kits", "categories", "progression").load();
        kits.seed("progression");

        database = new Database(this, "loadout.db");
        try {
            database.open();
        } catch (Exception failure) {
            getLogger().severe(Tr.t("Base de données inaccessible, désactivation : ") + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        storage.attach(database);
        KitRepository repository = storage.register(new KitRepository(database));
        KitJournal journal = new KitJournal(database);
        KitJournal.bind(journal);

        Wallet wallet = new Wallet();
        CrateBridge crates = new CrateBridge(getDataFolder().getParentFile());
        PlayerSettingsRepository preferences = new PlayerSettingsRepository(new File(getDataFolder(), "preferences.yml"));
        service = new KitService(repository, wallet, crates, preferences);
        service.animator(KitUnboxing::play);
        KitPlaceholders.bind(service);
        KitEditor editor = new KitEditor(kits, service);
        KitActions actions = new KitActions(service, crates);
        service.configure(kits.get());

        KitPreviewMenu preview = new KitPreviewMenu(actions);
        KitMenu menu = new KitMenu(actions, preview, preferences);
        KitMasteryMenu mastery = new KitMasteryMenu(actions);
        KitCollectionMenu collection = new KitCollectionMenu(actions, preview);
        KitHistoryMenu history = new KitHistoryMenu(journal);
        preview.bind(mastery);
        menu.bind(collection, history);
        KitEditorMenu editorMenu = new KitEditorMenu(service, editor, actions, preview, crates);
        KitAdminMenu adminMenu = new KitAdminMenu(service, editor, editorMenu, preview, wallet);
        KitProgressionMenu progression = new KitProgressionMenu(service, editor);
        editorMenu.bind(progression);
        adminMenu.bind(progression);
        KitCommand command = new KitCommand(actions, menu, preview, adminMenu, editor, wallet);
        command.bind(collection, history, mastery);
        command.onReload(this::loadSettings);
        command.importer(new KitImporter(service, editor));
        bind("kit", command);
        getServer().getPluginManager().registerEvents(new KitListener(actions), this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new LoadoutExpansion(this).register();
        }

        storage.start(SAVE_INTERVAL_SECONDS);
        service.start();
        if (!wallet.available()) {
            getLogger().info(Tr.t("Vault est absent : les kits payants et les récompenses en argent sont inactifs."));
        }
        getServer().getServicesManager().register(LoadoutApi.class, new LoadoutService(service, menu, preview), this,
                ServicePriority.Normal);
        if (settings.get().getBoolean("update-checker", true)) {
            new UpdateChecker(this, REPOSITORY, KitCommand.ADMIN).start();
        }
        telemetry.start(this, BSTATS_ID, Map.of(
                "kits", () -> String.valueOf(service.catalog().kits().size()),
                "economy", () -> wallet.available() ? "Vault" : "none"));
    }

    private ConfigFile loadSettings() {
        ConfigFile settings = new ConfigFile(this, "config.yml").load();
        Tr.configure(this, settings.get().getString("language", "en"));
        Palette.apply(settings.get().getConfigurationSection("theme"));
        KitPoints.configure(settings.get().getConfigurationSection("points"), settings.get().getConfigurationSection("teams"));
        new ConfigFile(this, "lang/messages_fr.yml").load();
        Messages.load(new ConfigFile(this, Tr.messagesFile(this)).load().get());
        return settings;
    }

    @Override
    public void onDisable() {
        telemetry.stop();
        getServer().getServicesManager().unregisterAll(this);
        if (service != null) {
            service.stop();
        }
        KitUnboxing.stopAll();
        KitTryOn.stopAll();
        storage.shutdown();
        if (database != null) {
            database.close();
        }
        FoliaGUI.shutdown();
    }

    private void bind(String name, CommandBase executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning(Tr.t("La commande ") + name + Tr.t(" est absente du plugin.yml"));
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
