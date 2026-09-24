package com.kirugoldzzzz.loadout.common.text;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class Palette {

    public static String PRIMARY_HEX = "#8FBF7F";
    public static String SECONDARY_HEX = "#E5C07B";
    public static String SUCCESS_HEX = "#4ADE80";
    public static String ERROR_HEX = "#F87171";
    public static String WARNING_HEX = "#FBBF24";
    public static String MONEY_HEX = "#FBBF24";
    public static String TEXT_HEX = "#C9D1D9";
    public static String MUTED_HEX = "#6E7681";

    public static String PRIMARY = tag(PRIMARY_HEX);
    public static String SECONDARY = tag(SECONDARY_HEX);
    public static String SUCCESS = tag(SUCCESS_HEX);
    public static String ERROR = tag(ERROR_HEX);
    public static String WARNING = tag(WARNING_HEX);
    public static String MONEY = tag(MONEY_HEX);
    public static String TEXT = tag(TEXT_HEX);
    public static String MUTED = tag(MUTED_HEX);

    public static String ACCENT = PRIMARY;
    public static String HIGHLIGHT = SECONDARY;
    public static String DANGER = ERROR;

    public static String GRADIENT_BRAND = "gradient:" + PRIMARY_HEX + ":" + SECONDARY_HEX;

    public static final String COIN = "⛁";
    public static final String ARROW = "»";
    public static final String POINTER = "❯";
    public static final String BACK = "❮";
    public static final String PIPE = "┃";
    public static final String CHECK = "✔";
    public static final String CROSS = "✖";

    private static TagResolver TAGS = resolver();

    private static TagResolver resolver() {
        return TagResolver.resolver(
                TagResolver.resolver("primary", styling(PRIMARY_HEX)),
                TagResolver.resolver("secondary", styling(SECONDARY_HEX)),
                TagResolver.resolver("success", styling(SUCCESS_HEX)),
                TagResolver.resolver("error", styling(ERROR_HEX)),
                TagResolver.resolver("warning", styling(WARNING_HEX)),
                TagResolver.resolver("money", styling(MONEY_HEX)),
                TagResolver.resolver("text", styling(TEXT_HEX)),
                TagResolver.resolver("muted", styling(MUTED_HEX)),
                Placeholder.unparsed("coin", COIN),
                Placeholder.unparsed("arrow", ARROW),
                Placeholder.unparsed("pointer", POINTER),
                Placeholder.unparsed("pipe", PIPE),
                Placeholder.unparsed("check", CHECK),
                Placeholder.unparsed("cross", CROSS),
                Placeholder.parsed("brand", "<" + GRADIENT_BRAND + "><b>Loadout</b></gradient>"));
    }

    private Palette() {
    }

    public static void apply(org.bukkit.configuration.ConfigurationSection theme) {
        if (theme == null) {
            return;
        }
        PRIMARY_HEX = hex(theme.getString("primary"), PRIMARY_HEX);
        SECONDARY_HEX = hex(theme.getString("secondary"), SECONDARY_HEX);
        SUCCESS_HEX = hex(theme.getString("success"), SUCCESS_HEX);
        ERROR_HEX = hex(theme.getString("error"), ERROR_HEX);
        WARNING_HEX = hex(theme.getString("warning"), WARNING_HEX);
        MONEY_HEX = hex(theme.getString("money"), MONEY_HEX);
        TEXT_HEX = hex(theme.getString("text"), TEXT_HEX);
        MUTED_HEX = hex(theme.getString("muted"), MUTED_HEX);
        PRIMARY = tag(PRIMARY_HEX);
        SECONDARY = tag(SECONDARY_HEX);
        SUCCESS = tag(SUCCESS_HEX);
        ERROR = tag(ERROR_HEX);
        WARNING = tag(WARNING_HEX);
        MONEY = tag(MONEY_HEX);
        TEXT = tag(TEXT_HEX);
        MUTED = tag(MUTED_HEX);
        ACCENT = PRIMARY;
        HIGHLIGHT = SECONDARY;
        DANGER = ERROR;
        GRADIENT_BRAND = "gradient:" + PRIMARY_HEX + ":" + SECONDARY_HEX;
        TAGS = resolver();
        Mini.invalidate();
    }

    static String hex(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        return value.matches("#[0-9A-Fa-f]{6}") ? value.toUpperCase(java.util.Locale.ROOT) : fallback;
    }

    public static TagResolver tags() {
        return TAGS;
    }

    public static String title(String text) {
        return "<" + GRADIENT_BRAND + "><b>" + text + "</b></gradient>";
    }

    public static String heading(String text) {
        return PRIMARY + "<b>" + text + "</b>";
    }

    public static String smallTitle(String text) {
        return title(Card.small(text));
    }

    public static String smallHeading(String text) {
        return heading(Card.small(text));
    }

    private static String tag(String hex) {
        return "<" + hex + ">";
    }

    private static Tag styling(String hex) {
        return Tag.styling(TextColor.fromHexString(hex));
    }
}
