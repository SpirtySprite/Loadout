package com.kirugoldzzzz.loadout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record KitCatalog(Map<String, Kit> kits, Map<String, KitCategory> categories, KitSettings settings,
                         List<String> problems, KitProgression progression) {

    public static final KitCatalog EMPTY = new KitCatalog(Map.of(), Map.of(), KitSettings.DEFAULTS, List.of(),
            KitProgression.NONE);

    public KitCatalog {
        Map<String, KitCategory> orderedCategories = new LinkedHashMap<>();
        List<KitCategory> sortedCategories = new ArrayList<>(categories == null ? List.of() : categories.values());
        sortedCategories.sort(Comparator.comparingInt(KitCategory::order).thenComparing(KitCategory::id));
        sortedCategories.forEach(category -> orderedCategories.put(category.id(), category));
        categories = Collections.unmodifiableMap(orderedCategories);

        List<Kit> sortedKits = new ArrayList<>(kits == null ? List.of() : kits.values());
        Map<String, KitCategory> lookup = orderedCategories;
        sortedKits.sort(Comparator
                .comparingInt((Kit kit) -> {
                    KitCategory category = kit.category() == null ? null : lookup.get(kit.category());
                    return category == null ? Integer.MAX_VALUE : category.order();
                })
                .thenComparingInt(Kit::order)
                .thenComparing(Kit::id));
        Map<String, Kit> orderedKits = new LinkedHashMap<>();
        sortedKits.forEach(kit -> orderedKits.put(kit.id(), kit));
        kits = Collections.unmodifiableMap(orderedKits);
        settings = settings == null ? KitSettings.DEFAULTS : settings;
        problems = problems == null ? List.of() : List.copyOf(problems);
        progression = progression == null ? KitProgression.NONE : progression;
    }

    public KitMastery masteryOf(Kit kit) {
        if (kit.mastery().enabled()) {
            return kit.mastery();
        }
        return kit.options().mastery() && kit.repeatable() ? progression.mastery() : KitMastery.NONE;
    }

    public List<String> featuredCandidates() {
        KitProgression.Featured featured = progression.featured();
        List<String> candidates = new ArrayList<>();
        for (Kit kit : kits.values()) {
            boolean listed = featured.kits().isEmpty() ? !kit.cost().free() : featured.kits().contains(kit.id());
            if (listed) {
                candidates.add(kit.id());
            }
        }
        return candidates;
    }

    public Optional<Kit> kit(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(kits.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<KitCategory> category(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(categories.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public List<Kit> inCategory(String category) {
        if (category == null || category.equals(KitCategory.ALL)) {
            return List.copyOf(kits.values());
        }
        return kits.values().stream().filter(kit -> category.equals(kit.category())).toList();
    }

    public String nameOf(String id) {
        return kit(id).map(Kit::name).orElse(null);
    }
}
