package com.kirugoldzzzz.loadout.common.storage;

public interface Store {

    void load();

    void flush();

    void flushNow();
}
