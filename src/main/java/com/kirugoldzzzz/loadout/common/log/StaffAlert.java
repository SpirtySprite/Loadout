package com.kirugoldzzzz.loadout.common.log;

import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class StaffAlert {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");
    private static final int WRAP = 38;

    public enum Severity {
        INFO(Palette.SECONDARY_HEX, "ℹ", "Info", Level.INFO),
        WARNING(Palette.WARNING_HEX, "⚠", "Attention", Level.WARNING),
        CRITICAL(Palette.ERROR_HEX, "✖", "Critique", Level.SEVERE);

        private final String hex;
        private final String icon;
        private final String label;
        private final Level level;

        Severity(String hex, String icon, String label, Level level) {
            this.hex = hex;
            this.icon = icon;
            this.label = label;
            this.level = level;
        }

        Level level() {
            return level;
        }
    }

    private record Row(String tint, String icon, String label, String value, String permission) {

        boolean visible(Predicate<String> allowed) {
            return permission == null || allowed.test(permission);
        }
    }

    private final LogTopic topic;
    private final Severity severity;
    private final String title;
    private final long at;
    private final List<Row> details = new ArrayList<>();
    private final List<Row> trace = new ArrayList<>();
    private String summary = "";
    private String command;
    private String hint;
    private String throttleKey;
    private long throttleMillis;
    private boolean lastDetail;
    private Throwable cause;

    private StaffAlert(LogTopic topic, Severity severity, String title, long at) {
        this.topic = topic == null ? LogTopic.GENERAL : topic;
        this.severity = severity == null ? Severity.WARNING : severity;
        this.title = title == null ? "" : title;
        this.at = at;
    }

    public static StaffAlert of(LogTopic topic, Severity severity, String title) {
        return new StaffAlert(topic, severity, title, System.currentTimeMillis());
    }

    public static StaffAlert warning(LogTopic topic, String title) {
        return of(topic, Severity.WARNING, title);
    }

    public static StaffAlert critical(LogTopic topic, String title) {
        return of(topic, Severity.CRITICAL, title);
    }

    public StaffAlert summary(String text) {
        summary = text == null ? "" : text;
        return this;
    }

    public StaffAlert player(String label, String name) {
        return row(details, Palette.SECONDARY, Card.PLAYER, label, name == null || name.isBlank() ? "Inconnu" : name);
    }

    public StaffAlert money(String label, double amount) {
        String value = Numbers.money(amount);
        if (Math.abs(amount) >= 1_000.0D) {
            value += " (" + Numbers.plain(amount) + ")";
        }
        return row(details, Palette.MONEY, Card.MONEY, label, value);
    }

    public StaffAlert count(String icon, String label, long value) {
        return row(details, Palette.PRIMARY, icon, label, Numbers.count(value));
    }

    public StaffAlert detail(String icon, String label, Object value) {
        return row(details, Palette.PRIMARY, icon, label, String.valueOf(value));
    }

    public StaffAlert location(String description) {
        return detail(Card.ZONE, "Position", description);
    }

    public StaffAlert error(Throwable failure) {
        if (failure == null) {
            return this;
        }
        cause = failure;
        String message = failure.getMessage();
        return row(details, Palette.ERROR, Palette.CROSS, "Erreur", failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : " : " + message));
    }

    public StaffAlert restricted(String permission) {
        List<Row> target = lastDetail ? details : trace;
        if (!target.isEmpty()) {
            Row last = target.remove(target.size() - 1);
            target.add(new Row(last.tint(), last.icon(), last.label(), last.value(), permission));
        }
        return this;
    }

    public StaffAlert reference(String label, Object value) {
        return row(trace, Palette.MUTED, Card.FLAG, label, String.valueOf(value));
    }

    public StaffAlert click(String command, String hint) {
        this.command = command;
        this.hint = hint == null ? "" : hint;
        return this;
    }

    public StaffAlert throttle(String key, long millis) {
        this.throttleKey = key;
        this.throttleMillis = millis;
        return this;
    }

    public void send() {
        NexusAlerts.dispatch(this);
    }

    LogTopic topic() {
        return topic;
    }

    Severity severity() {
        return severity;
    }

    long at() {
        return at;
    }

    Throwable cause() {
        return cause;
    }

    String throttleKey() {
        return throttleKey;
    }

    long throttleMillis() {
        return throttleMillis;
    }

    List<String> permissions() {
        List<String> found = new ArrayList<>();
        for (Row row : details) {
            if (row.permission() != null && !found.contains(row.permission())) {
                found.add(row.permission());
            }
        }
        for (Row row : trace) {
            if (row.permission() != null && !found.contains(row.permission())) {
                found.add(row.permission());
            }
        }
        return found;
    }

    String console() {
        StringBuilder text = new StringBuilder("ALERTE ").append(title);
        if (!summary.isBlank()) {
            text.append(" : ").append(summary);
        }
        for (Row row : details) {
            text.append(" | ").append(row.label()).append(" : ").append(row.value());
        }
        for (Row row : trace) {
            text.append(" | ").append(row.label()).append(" : ").append(row.value());
        }
        return text.toString();
    }

    List<String> hover(Predicate<String> allowed) {
        Card card = Card.of(severity.hex);
        card.raw(Card.title(severity.hex, Palette.PRIMARY_HEX, severity.icon + " " + Mini.escape(Card.small(title))));
        card.tag(topic.label() + " · " + severity.label);
        if (!summary.isBlank()) {
            card.blank();
            for (String part : wrap(summary)) {
                card.raw(Palette.TEXT + Mini.escape(part));
            }
        }
        if (!details.isEmpty()) {
            card.section("Détails");
            details.stream().filter(row -> row.visible(allowed)).forEach(row -> write(card, row));
        }
        card.section("Traçabilité");
        trace.stream().filter(row -> row.visible(allowed)).forEach(row -> write(card, row));
        write(card, new Row(Palette.MUTED, Card.TIME, "Heure", STAMP.format(Instant.ofEpochMilli(at)
                .atZone(ZoneId.systemDefault())), null));
        if (command != null) {
            card.blank();
            card.click("Clic", Mini.escape(hint));
        }
        return card.build();
    }

    Component render(Predicate<String> allowed) {
        List<Component> lines = new ArrayList<>();
        for (String line : hover(allowed)) {
            lines.add(Mini.uncached(line));
        }
        Component line = Messages.get("logs.alert",
                Mini.styled("logs", Messages.raw("logs.prefix")),
                Mini.styled("icon", "<" + severity.hex + "><b>" + severity.icon + "</b>"),
                Mini.value("topic", Card.small(topic.label())),
                Mini.value("title", title),
                Mini.value("summary", summary))
                .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), lines)));
        return command == null ? line : line.clickEvent(ClickEvent.runCommand(command));
    }

    private StaffAlert row(List<Row> target, String tint, String icon, String label, String value) {
        target.add(new Row(tint, icon, label == null ? "" : label, value == null ? "" : value, null));
        lastDetail = target == details;
        return this;
    }

    private static void write(Card card, Row row) {
        List<String> parts = wrap(row.value());
        card.stat(row.tint(), row.icon(), Mini.escape(row.label()), Mini.escape(parts.get(0)));
        for (int index = 1; index < parts.size(); index++) {
            card.raw(card.colour() + Palette.PIPE + "    " + row.tint() + Mini.escape(parts.get(index)));
        }
    }

    static List<String> wrap(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > WRAP) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                parts.add(word.substring(0, WRAP));
                word = word.substring(WRAP);
            }
            if (!current.isEmpty() && current.length() + 1 + word.length() > WRAP) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty() || parts.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }
}
