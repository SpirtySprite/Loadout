package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;

public final class KitPlaceholders {

    private static volatile KitService service;

    private KitPlaceholders() {
    }

    public static void bind(KitService value) {
        service = value;
    }

    public static String resolve(OfflinePlayer offline, String params) {
        KitService current = service;
        if (current == null || params == null) {
            return null;
        }
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("kit_featured")) {
            String featured = current.featured(System.currentTimeMillis());
            return featured == null ? "" : current.kit(featured).map(KitService::plain).orElse("");
        }
        if (key.equals("kit_featured_discount")) {
            return String.valueOf(current.catalog().progression().featured().discount());
        }
        if (key.equals("kits_total")) {
            return String.valueOf(current.catalog().kits().size());
        }
        Player player = offline == null ? null : offline.getPlayer();
        if (key.equals("kits_collection")) {
            return offline == null ? "0" : String.valueOf(current.collected(offline.getUniqueId()).size());
        }
        if (player == null) {
            return key.startsWith("kit") ? "" : null;
        }
        if (key.equals("kits_available")) {
            return String.valueOf(current.availableCount(player));
        }
        if (!key.startsWith("kit_")) {
            return null;
        }
        String rest = key.substring("kit_".length());
        int split = rest.lastIndexOf('_');
        if (split <= 0) {
            return null;
        }
        Optional<Kit> found = current.kit(rest.substring(0, split));
        if (found.isEmpty()) {
            return "";
        }
        Kit kit = found.get();
        long now = System.currentTimeMillis();
        KitViewer viewer = current.viewer(player);
        return switch (rest.substring(split + 1)) {
            case "status" -> Mini.plain(Mini.label(KitStyle.stateLine(current.status(viewer, kit, now), now)));
            case "ready" -> String.valueOf(current.status(viewer, kit, now).available());
            case "cooldown" -> Numbers.duration(current.status(viewer, kit, now).remaining());
            case "uses" -> String.valueOf(current.progress(viewer, kit, now).uses());
            case "tier" -> {
                KitService.Progress progress = current.progress(viewer, kit, now);
                KitMastery.Tier tier = progress.mastery().tier(progress.level());
                yield tier == null ? "" : tier.name();
            }
            case "level" -> String.valueOf(current.progress(viewer, kit, now).level());
            case "streak" -> String.valueOf(current.progress(viewer, kit, now).streak());
            case "best" -> String.valueOf(current.progress(viewer, kit, now).best());
            case "name" -> KitService.plain(kit);
            default -> null;
        };
    }
}
