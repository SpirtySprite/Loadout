package com.kirugoldzzzz.loadout.common.storage;

import com.kirugoldzzzz.loadout.common.diag.Diagnostics;
import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.NexusLog;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StorageManager {

    private static final long CHECKPOINT_SECONDS = 30L;

    private final List<Store> stores = new CopyOnWriteArrayList<>();

    private volatile Database database;
    private ScheduledTask task;
    private ScheduledTask checkpointTask;

    public void attach(Database database) {
        this.database = database;
    }

    public <T extends Store> T register(T store) {
        store.load();
        stores.add(store);
        return store;
    }

    public void start(long intervalSeconds) {
        stopTask();
        task = Scheduling.asyncTimer(this::flushAll, intervalSeconds, intervalSeconds);
        Database target = database;
        if (target != null) {
            checkpointTask = Scheduling.asyncTimer(target::checkpoint, CHECKPOINT_SECONDS, CHECKPOINT_SECONDS);
        }
    }

    public void shutdown() {
        stopTask();
        for (Store store : stores) {
            try {
                store.flushNow();
            } catch (RuntimeException failure) {
                NexusLog.error(LogTopic.STORAGE, "Sauvegarde finale impossible pour " + store.getClass().getSimpleName(), failure);
            }
        }
        stores.clear();
    }

    public void flushAll() {
        Diagnostics.time(Diagnostics.STORAGE_FLUSH, () -> stores.forEach(Store::flush));
    }

    public int pending() {
        int pending = 0;
        for (Store store : stores) {
            if (store instanceof SqlRepository<?> repository) {
                pending += repository.pending();
            }
        }
        return pending;
    }

    public int storeCount() {
        return stores.size();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (checkpointTask != null) {
            checkpointTask.cancel();
            checkpointTask = null;
        }
    }
}
