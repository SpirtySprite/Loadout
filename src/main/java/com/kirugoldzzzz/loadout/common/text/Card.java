package com.kirugoldzzzz.loadout.common.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Card {

    public static final String PLAYER = "☻";
    public static final String AMOUNT = "▣";
    public static final String MONEY = "⛃";
    public static final String TIME = "⌛";
    public static final String DONE = "✔";
    public static final String STAR = "✦";
    public static final String CHANCE = "⚄";
    public static final String FLAG = "⚑";
    public static final String ZONE = "◈";
    public static final String SORT = "☰";
    public static final String SEARCH = "✎";
    public static final String CATEGORY = "◆";
    public static final String CALL = "ᐅ";

    private static final int BAR_WIDTH = 10;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private final List<String> lines = new ArrayList<>();
    private final String colour;

    private Card(String hex) {
        this.colour = "<" + hex + ">";
    }

    public static Card of(String hex) {
        return new Card(hex);
    }

    public static String title(String hex, String accentHex, String text) {
        return "<gradient:" + hex + ":" + accentHex + "><b>" + text + "</b></gradient>";
    }

    public static String small(String text) {
        return SmallCaps.of(MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll(""));
    }

    public String colour() {
        return colour;
    }

    public Card tag(String text) {
        lines.add(Palette.MUTED + "[" + small(text) + "]");
        return this;
    }

    public Card section(String title) {
        blank();
        lines.add(colour + title + " :");
        return this;
    }

    public Card line(String text) {
        lines.add(bar() + Palette.TEXT + text);
        return this;
    }

    public Card lines(List<String> texts) {
        texts.forEach(this::line);
        return this;
    }

    public Card option(boolean selected, String text) {
        return line(selected ? colour + "● " + text : Palette.MUTED + "○ " + text);
    }

    public Card stat(String icon, String label, Object value) {
        return stat(colour, icon, label, value);
    }

    public Card stat(String tint, String icon, String label, Object value) {
        lines.add(bar() + tint + icon + " " + Palette.TEXT + label + " : " + tint + value);
        return this;
    }

    public Card money(String label, double amount) {
        return stat(Palette.MONEY, MONEY, label, Numbers.money(amount));
    }

    public Card count(String icon, String label, long value) {
        return stat(icon, label, Numbers.count(value));
    }

    public Card progress(long current, long total) {
        long safeTotal = Math.max(1L, total);
        long clamped = Math.max(0L, Math.min(current, safeTotal));
        int filled = (int) Math.round((double) clamped / safeTotal * BAR_WIDTH);
        int percent = (int) Math.round((double) clamped / safeTotal * 100.0D);
        lines.add(bar() + colour + "▰".repeat(filled) + Palette.MUTED + "▱".repeat(BAR_WIDTH - filled)
                + " " + Palette.TEXT + percent + "%");
        return this;
    }

    public Card note(String tint, String icon, String text) {
        lines.add(noteLine(tint, icon, text));
        return this;
    }

    public Card click(String action) {
        lines.add(clickLine(action));
        return this;
    }

    public Card click(String button, String action) {
        lines.add(clickLine(button, action));
        return this;
    }

    public Card waiting(String text) {
        lines.add(waitingLine(text));
        return this;
    }

    public Card deny(String text) {
        lines.add(denyLine(text));
        return this;
    }

    public static String noteLine(String tint, String icon, String text) {
        return tint + icon + " " + Palette.TEXT + text;
    }

    public static String clickLine(String action) {
        return clickLine(Tr.t("Cliquez"), action);
    }

    public static String clickLine(String button, String action) {
        return Palette.WARNING + CALL + " <b>" + small(button) + "</b> " + Palette.TEXT + action;
    }

    public static String waitingLine(String text) {
        return noteLine(Palette.WARNING, TIME, text);
    }

    public static String denyLine(String text) {
        return noteLine(Palette.ERROR, Palette.CROSS, text);
    }

    public Card raw(String line) {
        lines.add(line);
        return this;
    }

    public Card blank() {
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
            lines.add("");
        }
        return this;
    }

    public List<String> build() {
        List<String> built = new ArrayList<>(lines);
        while (!built.isEmpty() && built.get(built.size() - 1).isEmpty()) {
            built.remove(built.size() - 1);
        }
        return List.copyOf(built);
    }

    private String bar() {
        return colour + Palette.PIPE + " ";
    }
}
