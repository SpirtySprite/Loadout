package com.kirugoldzzzz.loadout.common.log;

import com.kirugoldzzzz.loadout.common.text.Tr;

public enum LogTopic {

    GENERAL("general", Tr.t("Général")),
    STORAGE("stockage", Tr.t("Stockage")),
    CONFIG("configuration", Tr.t("Configuration")),
    ECONOMY("economie", Tr.t("Économie")),
    SENTINEL("sentinelle", Tr.t("Sentinelle")),
    SPAWNERS("generateurs", Tr.t("Générateurs")),
    CRATES("caisses", Tr.t("Caisses")),
    KITS("kits", Tr.t("Kits")),
    JOBS("metiers", Tr.t("Métiers")),
    BACKUPS("sauvegardes", Tr.t("Sauvegardes")),
    NPC("pnj", "PNJ"),
    REGIONS("regions", Tr.t("Régions")),
    ITEMS("objets", Tr.t("Objets")),
    MENUS("menus", Tr.t("Menus")),
    HOLOGRAMS("hologrammes", Tr.t("Hologrammes")),
    STAFF("moderation", Tr.t("Modération"));

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
