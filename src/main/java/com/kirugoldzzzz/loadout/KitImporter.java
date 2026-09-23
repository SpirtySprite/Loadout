package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.item.ItemSpec;
import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.NexusLog;
import com.kirugoldzzzz.loadout.common.text.Tr;
import com.kirugoldzzzz.loadout.importer.Imported;
import com.kirugoldzzzz.loadout.importer.KitSource;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class KitImporter {

    record Summary(int kits, int items, int claims, List<String> warnings) {
    }

    private final KitService service;
    private final KitEditor editor;

    KitImporter(KitService service, KitEditor editor) {
        this.service = service;
        this.editor = editor;
    }

    static File folder(KitSource source) {
        return new File(Bukkit.getPluginsFolder(), source.plugin());
    }

    Summary write(Imported.Result result) {
        List<String> warnings = new ArrayList<>(result.warnings());
        Map<String, String> written = editor.importKits(result.kits(), item -> build(item, warnings), warnings);
        int items = 0;
        for (Imported.Kit kit : result.kits()) {
            if (written.containsKey(kit.id())) {
                items += kit.items().size();
            }
        }
        int claims = 0;
        for (Map.Entry<UUID, Map<String, Long>> player : result.claims().entrySet()) {
            for (Map.Entry<String, Long> claim : player.getValue().entrySet()) {
                String kit = written.get(claim.getKey());
                if (kit != null) {
                    service.repository().record(player.getKey(), kit, claim.getValue());
                    claims++;
                }
            }
        }
        for (String warning : warnings) {
            NexusLog.warn(LogTopic.KITS, "[" + result.source() + "] " + warning);
        }
        return new Summary(written.size(), items, claims, List.copyOf(warnings));
    }

    static ItemStack build(Imported.Item item, List<String> warnings) {
        ItemStack stack;
        if (item.serialized() != null) {
            try {
                stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(item.serialized()));
            } catch (IllegalArgumentException invalid) {
                warnings.add(Tr.t("Objet sérialisé illisible, remplacé par de la pierre"));
                stack = new ItemStack(Material.STONE);
            }
            return stack;
        }
        if (item.argument() != null) {
            try {
                stack = Bukkit.getItemFactory().createItemStack(item.argument());
            } catch (IllegalArgumentException invalid) {
                warnings.add(Tr.t("Objet non reconnu, remplacé par de la pierre : ") + item.argument());
                stack = new ItemStack(Material.STONE);
            }
        } else {
            if (Material.matchMaterial(item.material()) == null) {
                warnings.add(Tr.t("Matériau inconnu, remplacé par de la pierre : ") + item.material());
            }
            YamlConfiguration spec = new YamlConfiguration();
            item.spec().forEach(spec::set);
            stack = ItemSpec.read(spec, Material.STONE);
        }
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), item.amount())));
        return stack;
    }
}
