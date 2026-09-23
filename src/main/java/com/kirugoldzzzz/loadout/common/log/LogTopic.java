package com.kirugoldzzzz.loadout.common.log;

public enum LogTopic {

    GENERAL("general", "Général"),
    STORAGE("stockage", "Stockage"),
    CONFIG("configuration", "Configuration"),
    ECONOMY("economie", "Économie"),
    SENTINEL("sentinelle", "Sentinelle"),
    SPAWNERS("generateurs", "Générateurs"),
    CRATES("caisses", "Caisses"),
    KITS("kits", "Kits"),
    JOBS("metiers", "Métiers"),
    BACKUPS("sauvegardes", "Sauvegardes"),
    NPC("pnj", "PNJ"),
    REGIONS("regions", "Régions"),
    ITEMS("objets", "Objets"),
    MENUS("menus", "Menus"),
    HOLOGRAMS("hologrammes", "Hologrammes"),
    STAFF("moderation", "Modération");

    private final String id;
    private final String label;

    LogTopic(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String permission() {
        return NexusAlerts.ALL + "." + id;
    }
}
