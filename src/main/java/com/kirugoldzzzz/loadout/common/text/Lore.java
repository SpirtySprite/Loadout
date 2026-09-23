package com.kirugoldzzzz.loadout.common.text;

import java.util.ArrayList;
import java.util.List;

public final class Lore {

    private static final int BAR_WIDTH = 10;
    private static final String FILLED = "▰";
    private static final String EMPTY = "▱";

    private final List<String> lines = new ArrayList<>();

    private Lore() {
    }

    public static Lore create() {
        return new Lore();
    }

    public Lore blank() {
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
            lines.add("");
        }
        return this;
    }

    public Lore raw(String value) {
        lines.add(value);
        return this;
    }

    public Lore text(String value) {
        lines.add(bar() + Palette.TEXT + value);
        return this;
    }

    public Lore section(String value) {
        lines.add(Palette.PRIMARY + SmallCaps.of(value) + " :");
        return this;
    }

    public Lore divider() {
        lines.add("<#30363D>━━━━━━━━━━━━━━━━━━━━");
        return this;
    }

    public Lore lines(List<String> values) {
        for (String value : values) {
            if (value.isEmpty()) {
                blank();
            } else {
                lines.add(value);
            }
        }
        return this;
    }

    public Lore entry(String label, Object value) {
        lines.add(bar() + Palette.PRIMARY + Card.CATEGORY + " " + Palette.TEXT + label + " : "
                + Palette.PRIMARY + value);
        return this;
    }

    public Lore smallEntry(String label, Object value) {
        return entry(SmallCaps.of(label), value);
    }

    public Lore highlight(String label, Object value) {
        lines.add(bar() + Palette.SECONDARY + Card.STAR + " " + Palette.TEXT + label + " : "
                + Palette.SECONDARY + value);
        return this;
    }

    public Lore money(String label, double amount) {
        lines.add(bar() + Palette.MONEY + Card.MONEY + " " + Palette.TEXT + label + " : "
                + Palette.MONEY + Numbers.money(amount));
        return this;
    }

    public Lore smallMoney(String label, double amount) {
        return money(SmallCaps.of(label), amount);
    }

    public Lore count(String label, long value) {
        lines.add(bar() + Palette.PRIMARY + Card.AMOUNT + " " + Palette.TEXT + label + " : "
                + Palette.PRIMARY + Numbers.count(value));
        return this;
    }

    public Lore smallCount(String label, long value) {
        return count(SmallCaps.of(label), value);
    }

    public Lore ratio(String label, long current, long total) {
        lines.add(bar() + Palette.SECONDARY + Card.DONE + " " + Palette.TEXT + label + " : "
                + Palette.SECONDARY + Numbers.count(current)
                + Palette.MUTED + " / " + Palette.SECONDARY + Numbers.count(total));
        return this;
    }

    public Lore smallRatio(String label, long current, long total) {
        return ratio(SmallCaps.of(label), current, total);
    }

    public Lore progress(long current, long total) {
        long safeTotal = Math.max(1L, total);
        long clamped = Math.max(0L, Math.min(current, safeTotal));
        int filled = (int) Math.round((double) clamped / safeTotal * BAR_WIDTH);
        int percent = (int) Math.round((double) clamped / safeTotal * 100.0D);
        lines.add(bar() + Palette.SUCCESS + FILLED.repeat(filled)
                + Palette.MUTED + EMPTY.repeat(BAR_WIDTH - filled)
                + " " + Palette.TEXT + percent + "%");
        return this;
    }

    public Lore state(String label, boolean active, String on, String off) {
        lines.add(bar() + (active ? Palette.SUCCESS : Palette.ERROR) + Card.FLAG + " "
                + Palette.TEXT + label + " : "
                + (active ? Palette.SUCCESS + on : Palette.ERROR + off));
        return this;
    }

    public Lore smallState(String label, boolean active, String on, String off) {
        return state(SmallCaps.of(label), active, SmallCaps.of(on), SmallCaps.of(off));
    }

    public Lore action(String value) {
        lines.add(Palette.SUCCESS + Card.CALL + " " + Palette.TEXT + value);
        return this;
    }

    public Lore smallAction(String value) {
        return action(SmallCaps.of(value));
    }

    public Lore hint(String value) {
        lines.add(Card.noteLine(Palette.SECONDARY, Card.STAR, value));
        return this;
    }

    public Lore smallHint(String value) {
        return hint(SmallCaps.of(value));
    }

    public Lore warn(String value) {
        lines.add(Card.noteLine(Palette.WARNING, Card.FLAG, value));
        return this;
    }

    public Lore smallWarn(String value) {
        return warn(SmallCaps.of(value));
    }

    public Lore deny(String value) {
        lines.add(Card.denyLine(value));
        return this;
    }

    public Lore smallDeny(String value) {
        return deny(SmallCaps.of(value));
    }

    public Lore click(String button, String value) {
        lines.add(Card.clickLine(button, value));
        return this;
    }

    public Lore smallClick(String button, String value) {
        return click(SmallCaps.of(button), SmallCaps.of(value));
    }

    public Lore denyClick(String button, String value) {
        lines.add(Palette.ERROR + Card.CALL + " <b>" + Card.small(button) + "</b> "
                + Palette.TEXT + value);
        return this;
    }

    public Lore smallDenyClick(String button, String value) {
        return denyClick(SmallCaps.of(button), SmallCaps.of(value));
    }

    public List<String> build() {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return List.copyOf(lines);
    }

    private String bar() {
        return Palette.PRIMARY + Palette.PIPE + " ";
    }
}
