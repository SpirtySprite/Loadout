package com.kirugoldzzzz.loadout;

public final class KitSlots {

    public static final int HOTBAR = 9;
    public static final int STORAGE = 36;
    public static final int BOOTS = 36;
    public static final int LEGGINGS = 37;
    public static final int CHESTPLATE = 38;
    public static final int HELMET = 39;
    public static final int OFFHAND = 40;
    public static final int SIZE = 41;

    private KitSlots() {
    }

    public static boolean valid(int slot) {
        return slot >= 0 && slot < SIZE;
    }

    public static boolean armour(int slot) {
        return slot >= BOOTS && slot <= HELMET;
    }

    public static boolean equipment(int slot) {
        return slot >= BOOTS;
    }

    public static String label(int slot) {
        return switch (slot) {
            case BOOTS -> "Bottes";
            case LEGGINGS -> "Jambières";
            case CHESTPLATE -> "Plastron";
            case HELMET -> "Casque";
            case OFFHAND -> "Main secondaire";
            default -> slot < HOTBAR ? "Barre rapide " + (slot + 1) : "Inventaire " + (slot - HOTBAR + 1);
        };
    }

    public static int editorSlot(int slot) {
        if (slot >= HOTBAR && slot < STORAGE) {
            return slot - HOTBAR;
        }
        if (slot >= 0 && slot < HOTBAR) {
            return 27 + slot;
        }
        return switch (slot) {
            case HELMET -> 36;
            case CHESTPLATE -> 37;
            case LEGGINGS -> 38;
            case BOOTS -> 39;
            case OFFHAND -> 40;
            default -> -1;
        };
    }

    public static int kitSlot(int editorSlot) {
        if (editorSlot >= 0 && editorSlot < 27) {
            return editorSlot + HOTBAR;
        }
        if (editorSlot >= 27 && editorSlot < STORAGE) {
            return editorSlot - 27;
        }
        return switch (editorSlot) {
            case 36 -> HELMET;
            case 37 -> CHESTPLATE;
            case 38 -> LEGGINGS;
            case 39 -> BOOTS;
            case 40 -> OFFHAND;
            default -> -1;
        };
    }
}
