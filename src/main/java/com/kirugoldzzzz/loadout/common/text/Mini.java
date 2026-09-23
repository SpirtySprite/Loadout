package com.kirugoldzzzz.loadout.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class Mini {

    private static final int LABEL_CACHE = 4096;

    private static final Map<String, Component> LABELS = new ConcurrentHashMap<>();
    private static final Map<String, Component> PARSED = new ConcurrentHashMap<>();
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .build();

    private Mini() {
    }

    public static Component parse(String input, TagResolver... resolvers) {
        if (resolvers.length > 0) {
            return MINI.deserialize(input == null ? "" : input, merge(resolvers));
        }
        return cached(PARSED, input == null ? "" : input, key -> MINI.deserialize(key, Palette.tags()));
    }

    private static Component cached(Map<String, Component> cache, String key, Function<String, Component> render) {
        Component hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        Component rendered = render.apply(key);
        if (cache.size() >= LABEL_CACHE) {
            trim(cache);
        }
        Component raced = cache.putIfAbsent(key, rendered);
        return raced == null ? rendered : raced;
    }

    private static void trim(Map<String, Component> cache) {
        int drop = LABEL_CACHE / 4;
        Iterator<String> keys = cache.keySet().iterator();
        while (drop-- > 0 && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private static TagResolver merge(TagResolver[] extra) {
        if (extra.length == 0) {
            return Palette.tags();
        }
        TagResolver[] all = new TagResolver[extra.length + 1];
        all[0] = Palette.tags();
        System.arraycopy(extra, 0, all, 1, extra.length);
        return TagResolver.resolver(all);
    }

    public static Component label(String input, TagResolver... resolvers) {
        if (resolvers.length > 0) {
            return render(input, resolvers);
        }
        return cached(LABELS, input == null ? "" : input, Mini::render);
    }

    public static int cachedLabels() {
        return LABELS.size();
    }

    public static int cachedParsed() {
        return PARSED.size();
    }

    public static void invalidate() {
        LABELS.clear();
        PARSED.clear();
    }

    private static Component render(String input, TagResolver... resolvers) {
        return parse(input, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> labels(List<String> lines, TagResolver... resolvers) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(label(line, resolvers));
        }
        return components;
    }

    public static Component uncached(String input) {
        return MINI.deserialize(input == null ? "" : input, Palette.tags());
    }

    public static Component live(String input) {
        return uncached(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> live(List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(live(line));
        }
        return components;
    }

    public static String escape(String text) {
        return text == null ? "" : MINI.escapeTags(text, Palette.tags());
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static TagResolver value(String key, String replacement) {
        return Placeholder.unparsed(key, replacement == null ? "" : replacement);
    }

    public static TagResolver styled(String key, String replacement) {
        return Placeholder.parsed(key, replacement == null ? "" : replacement);
    }

    public static TagResolver component(String key, Component replacement) {
        return Placeholder.component(key, replacement);
    }
}
