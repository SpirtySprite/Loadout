package com.kirugoldzzzz.loadout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record KitRewards(double money, long shards, int levels, Map<String, Integer> keys, List<KitDispatch> commands,
                         List<KitEffect> effects, List<String> messages, List<String> lines) {

    public static final KitRewards NONE = new KitRewards(0.0D, 0L, 0, Map.of(), List.of(), List.of(), List.of(),
            List.of());

    public KitRewards {
        money = Double.isFinite(money) ? Math.max(0.0D, money) : 0.0D;
        shards = Math.max(0L, shards);
        levels = Math.max(0, levels);
        Map<String, Integer> cleaned = new LinkedHashMap<>();
        if (keys != null) {
            keys.forEach((crate, amount) -> {
                if (crate != null && !crate.isBlank() && amount != null && amount > 0) {
                    cleaned.merge(crate.trim().toLowerCase(Locale.ROOT), amount, Integer::sum);
                }
            });
        }
        keys = Collections.unmodifiableMap(cleaned);
        commands = commands == null ? List.of() : commands.stream().filter(KitDispatch::valid).toList();
        effects = effects == null ? List.of() : List.copyOf(effects);
        messages = messages == null ? List.of() : List.copyOf(messages);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public boolean empty() {
        return money <= 0.0D && shards <= 0L && levels <= 0 && keys.isEmpty() && commands.isEmpty()
                && effects.isEmpty();
    }
}
