package com.kirugoldzzzz.loadout;

import org.bukkit.Material;

import java.util.List;

public record KitCategory(String id, String name, Material icon, int order, List<String> description) {

    public static final String ALL = "tous";

    public KitCategory {
        icon = icon == null ? Material.CHEST : icon;
        description = description == null ? List.of() : List.copyOf(description);
        name = name == null || name.isBlank() ? id : name;
    }
}
