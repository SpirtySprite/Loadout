package com.kirugoldzzzz.loadout;

public record PlayerSettings(boolean reminders) {

    public enum Setting {
        KIT_REMINDERS
    }

    public boolean enabled(Setting setting) {
        return setting != Setting.KIT_REMINDERS || reminders;
    }

    public boolean sounds() {
        return true;
    }

    public boolean particles() {
        return true;
    }
}
