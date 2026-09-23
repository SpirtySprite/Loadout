package com.kirugoldzzzz.loadout;

public record KitOptions(boolean autoEquip, boolean protectItems, boolean announce, boolean animation,
                         boolean firstJoin, boolean respawn, boolean giftable, boolean voucher,
                         boolean voucherIgnoresLimits, boolean confirm, boolean roulette, boolean team,
                         boolean streaks, boolean mastery) {

    public static final KitOptions DEFAULTS = new KitOptions(true, false, false, true, false, false, false, false,
            true, true, false, false, true, true);

    public enum Flag {
        AUTO_EQUIP("auto-equip", "Équipement automatique", "Enfile armure et bouclier si les emplacements sont libres"),
        PROTECT_ITEMS("protect-items", "Objets invendables", "Les objets du kit ne passent ni au /sell ni aux enchères"),
        ANNOUNCE("announce", "Annonce publique", "Tout le serveur voit qui récupère ce kit"),
        ANIMATION("animation", "Animation d'ouverture", "Coffre holographique et objets qui volent vers le joueur"),
        FIRST_JOIN("first-join", "Première connexion", "Donné automatiquement à la toute première connexion"),
        RESPAWN("respawn", "À la réapparition", "Redonné après une mort dès qu'il est disponible"),
        GIFTABLE("giftable", "Peut être offert", "Un joueur peut payer ce kit pour un autre"),
        VOUCHER("voucher", "Bons physiques", "Le kit existe aussi sous forme de bon à utiliser"),
        VOUCHER_IGNORES_LIMITS("voucher-ignores-limits", "Bons sans délai", "Un bon ignore recharge, limites et conditions"),
        CONFIRM("confirm", "Confirmation d'achat", "Demande une confirmation quand le kit coûte quelque chose"),
        ROULETTE("roulette", "Roulette mystère", "Le tirage aléatoire se révèle dans une roulette animée"),
        TEAM("team", "Kit d'équipe", "Une seule recharge partagée par toute l'équipe"),
        STREAKS("streaks", "Séries", "Récupérer à chaque remise à zéro fait grimper un bonus"),
        MASTERY("mastery", "Maîtrise", "Le kit monte en palier à force d'être récupéré");

        private final String key;
        private final String label;
        private final String description;

        Flag(String key, String label, String description) {
            this.key = key;
            this.label = label;
            this.description = description;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }
    }

    public boolean enabled(Flag flag) {
        return switch (flag) {
            case AUTO_EQUIP -> autoEquip;
            case PROTECT_ITEMS -> protectItems;
            case ANNOUNCE -> announce;
            case ANIMATION -> animation;
            case FIRST_JOIN -> firstJoin;
            case RESPAWN -> respawn;
            case GIFTABLE -> giftable;
            case VOUCHER -> voucher;
            case VOUCHER_IGNORES_LIMITS -> voucherIgnoresLimits;
            case CONFIRM -> confirm;
            case ROULETTE -> roulette;
            case TEAM -> team;
            case STREAKS -> streaks;
            case MASTERY -> mastery;
        };
    }

    public static boolean fallback(Flag flag) {
        return DEFAULTS.enabled(flag);
    }
}
