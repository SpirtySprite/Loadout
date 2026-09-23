package com.kirugoldzzzz.loadout.common.text;

public final class SmallCaps {

    private SmallCaps() {
    }

    public static String of(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            result.append(map(value.charAt(index)));
        }
        return result.toString();
    }

    private static char map(char value) {
        return switch (value) {
            case 'a', 'A' -> 'ᴀ';
            case 'b', 'B' -> 'ʙ';
            case 'c', 'C' -> 'ᴄ';
            case 'd', 'D' -> 'ᴅ';
            case 'e', 'E' -> 'ᴇ';
            case 'f', 'F' -> 'ꜰ';
            case 'g', 'G' -> 'ɢ';
            case 'h', 'H' -> 'ʜ';
            case 'i', 'I' -> 'ɪ';
            case 'j', 'J' -> 'ᴊ';
            case 'k', 'K' -> 'ᴋ';
            case 'l', 'L' -> 'ʟ';
            case 'm', 'M' -> 'ᴍ';
            case 'n', 'N' -> 'ɴ';
            case 'o', 'O' -> 'ᴏ';
            case 'p', 'P' -> 'ᴘ';
            case 'q', 'Q' -> 'ǫ';
            case 'r', 'R' -> 'ʀ';
            case 's', 'S' -> 's';
            case 't', 'T' -> 'ᴛ';
            case 'u', 'U' -> 'ᴜ';
            case 'v', 'V' -> 'ᴠ';
            case 'w', 'W' -> 'ᴡ';
            case 'x', 'X' -> 'x';
            case 'y', 'Y' -> 'ʏ';
            case 'z', 'Z' -> 'ᴢ';
            default -> value;
        };
    }
}
