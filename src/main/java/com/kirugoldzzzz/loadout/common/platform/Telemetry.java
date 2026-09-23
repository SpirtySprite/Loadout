package com.kirugoldzzzz.loadout.common.platform;

import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.Callable;

public final class Telemetry {

    private Metrics metrics;

    public boolean start(JavaPlugin plugin, int serviceId, Map<String, Callable<String>> pies) {
        if (serviceId <= 0 || metrics != null) {
            return false;
        }
        metrics = new Metrics(plugin, serviceId);
        metrics.addCustomChart(new SimplePie("language", Tr::language));
        pies.forEach((id, value) -> metrics.addCustomChart(new SimplePie(id, value)));
        return true;
    }

    public void stop() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }
}
