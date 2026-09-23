package com.kirugoldzzzz.loadout.common.text;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class Palette {

    public static final String PRIMARY_HEX = "#A78BFA";
    public static final String SECONDARY_HEX = "#22D3EE";
    public static final String SUCCESS_HEX = "#4ADE80";
    public static final String ERROR_HEX = "#F87171";
    public static final String WARNING_HEX = "#FBBF24";
    public static final String MONEY_HEX = "#FBBF24";
    public static final String TEXT_HEX = "#C9D1D9";
    public static final String MUTED_HEX = "#6E7681";

    public static final String PRIMARY = tag(PRIMARY_HEX);
    public static final String SECONDARY = tag(SECONDARY_HEX);
    public static final String SUCCESS = tag(SUCCESS_HEX);
    public static final String ERROR = tag(ERROR_HEX);
    public static final String WARNING = tag(WARNING_HEX);
    public static final String MONEY = tag(MONEY_HEX);
    public static final String TEXT = tag(TEXT_HEX);
    public static final String MUTED = tag(MUTED_HEX);

    public static final String ACCENT = PRIMARY;
    public static final String HIGHLIGHT = SECONDARY;
    public static final String DANGER = ERROR;

    public static final String GRADIENT_BRAND = "gradient:" + PRIMARY_HEX + ":" + SECONDARY_HEX;

    public static final String COIN = "Ⓝ";
    public static final String ARROW = "»";
    public static final String POINTER = "❯";
    public static final String BACK = "❮";
    public static final String PIPE = "┃";
    public static final String CHECK = "✔";
    public static final String CROSS = "✖";

    private static final TagResolver TAGS = TagResolver.resolver(
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

    private Palette() {
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
