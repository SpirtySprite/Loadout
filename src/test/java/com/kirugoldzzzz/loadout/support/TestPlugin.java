package com.kirugoldzzzz.loadout.support;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TestPlugin {

    private TestPlugin() {
    }

    public static Plugin at(File dataFolder) {
        Logger logger = Logger.getLogger("NexusTest");
        logger.setLevel(Level.OFF);
        return (Plugin) Proxy.newProxyInstance(
                TestPlugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDataFolder" -> dataFolder;
                    case "getLogger" -> logger;
                    case "getName" -> "NexusTest";
                    case "isEnabled" -> true;
                    case "toString" -> "TestPlugin";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }
}
