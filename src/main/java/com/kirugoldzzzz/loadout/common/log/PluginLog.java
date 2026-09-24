package com.kirugoldzzzz.loadout.common.log;

import com.kirugoldzzzz.loadout.common.text.Tr;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class PluginLog {

    public static final String NAME = "Loadout";

    private static final Logger LOGGER = Logger.getLogger(NAME);

    private PluginLog() {
    }

    public static void info(LogTopic topic, String message) {
        write(Level.INFO, topic, message, null);
    }

    public static void warn(LogTopic topic, String message) {
        write(Level.WARNING, topic, message, null);
    }

    public static void warn(LogTopic topic, String message, Throwable failure) {
        write(Level.WARNING, topic, message, failure);
    }

    public static void error(LogTopic topic, String message) {
        write(Level.SEVERE, topic, message, null);
    }

    public static void error(LogTopic topic, String message, Throwable failure) {
        write(Level.SEVERE, topic, message, failure);
    }

    public static void debug(LogTopic topic, String message) {
        write(Level.FINE, topic, message, null);
    }

    public static String format(LogTopic topic, String message) {
        return "[" + (topic == null ? LogTopic.GENERAL : topic).label() + "] " + (message == null ? "" : message);
    }

    static void write(Level level, LogTopic topic, String message, Throwable failure) {
        String line = format(topic, message);
        if (level.intValue() >= Level.INFO.intValue()) {
            LogBuffer.record(level, topic, message, failure);
        }
        try {
            if (!LOGGER.isLoggable(level)) {
                return;
            }
            if (failure == null) {
                LOGGER.log(level, line);
            } else {
                LOGGER.log(level, line, failure);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            System.err.println("[" + NAME + "] " + line + (failure == null ? "" : " : " + failure));
        }
    }
}
