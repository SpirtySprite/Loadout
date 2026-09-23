package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KitContentsEditor implements InventoryHolder {

    static final int SIZE = 54;
    static final int EDITABLE = KitSlots.SIZE;
    static final int CAPTURE = 42;
    static final int CLEAR = 43;
    static final int CANCEL = 45;
    static final int INFO = 49;
    static final int SAVE = 53;

    private final KitEditor editor;
    private final String kit;
    private final Runnable back;
    private final Inventory inventory;
    private boolean discard;
    private boolean finished;

    KitContentsEditor(KitEditor editor, Kit kit, Runnable back) {
        this.editor = editor;
        this.kit = kit.id();
        this.back = back;
        this.inventory = Bukkit.createInventory(this, SIZE, Mini.parse(Palette.smallTitle("Contenu du kit")));
        kit.contents().forEach((slot, item) -> {
            int editorSlot = KitSlots.editorSlot(slot);
            if (editorSlot >= 0) {
                inventory.setItem(editorSlot, item.clone());
            }
        });
        decorate();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void open(Player player) {
        player.openInventory(inventory);
    }

    private void decorate() {
        ItemStack pane = KitStyle.decorate(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), " ", List.of(), false);
        for (int slot = EDITABLE; slot < SIZE; slot++) {
            inventory.setItem(slot, pane);
        }
        inventory.setItem(CAPTURE, KitStyle.decorate(new ItemStack(Material.ARMOR_STAND),
                Palette.heading("Copier mon inventaire"), Card.of(KitStyle.HEX).blank()
                        .line("Remplace le contenu par une copie")
                        .line("exacte de votre inventaire, armure")
                        .line("et main secondaire comprises.")
                        .blank().click("pour copier").build(), false));
        inventory.setItem(CLEAR, KitStyle.decorate(new ItemStack(Material.LAVA_BUCKET),
                Palette.ERROR + "<b>Tout vider</b>", Card.of(Palette.ERROR_HEX).blank()
                        .line("Retire tous les objets du kit.")
                        .blank().click("Shift clic", "pour vider").build(), false));
        inventory.setItem(CANCEL, KitStyle.decorate(new ItemStack(Material.BARRIER),
                Palette.ERROR + "<b>Annuler</b>", Card.of(Palette.ERROR_HEX).blank()
                        .line("Ferme sans enregistrer.")
                        .blank().click("pour annuler").build(), false));
        inventory.setItem(INFO, KitStyle.decorate(new ItemStack(Material.BOOK), Palette.heading("Disposition"),
                Card.of(KitStyle.HEX).blank()
                        .line("Lignes 1 à 3 : inventaire")
                        .line("Ligne 4 : barre rapide")
                        .line("Ligne 5 : casque, plastron,")
                        .line("jambières, bottes, main secondaire")
                        .blank()
                        .line("Chaque objet est remis au même")
                        .line("emplacement s'il est libre.")
                        .blank()
                        .line("Fermer le menu enregistre.")
                        .build(), false));
        inventory.setItem(SAVE, KitStyle.decorate(new ItemStack(Material.LIME_CONCRETE),
                Palette.SUCCESS + "<b>Enregistrer</b>", Card.of(Palette.SUCCESS_HEX).blank()
                        .line("Enregistre le contenu et revient")
                        .line("à l'éditeur du kit.")
                        .blank().click("pour enregistrer").build(), true));
    }

    void click(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        InventoryAction action = event.getAction();
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw >= SIZE) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack moving = event.getCurrentItem();
                if (moving != null && !moving.getType().isAir()) {
                    int free = firstFree();
                    if (free >= 0) {
                        inventory.setItem(free, moving.clone());
                        event.setCurrentItem(null);
                    }
                }
            }
            return;
        }
        if (raw < EDITABLE) {
            return;
        }
        event.setCancelled(true);
        switch (raw) {
            case CAPTURE -> {
                Guis.click(player);
                capture(player.getInventory());
            }
            case CLEAR -> {
                if (event.isShiftClick()) {
                    Guis.click(player);
                    for (int slot = 0; slot < EDITABLE; slot++) {
                        inventory.setItem(slot, null);
                    }
                } else {
                    Guis.deny(player);
                }
            }
            case CANCEL -> {
                Guis.click(player);
                discard = true;
                player.closeInventory();
            }
            case SAVE -> {
                Guis.success(player);
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    void drag(InventoryDragEvent event) {
        for (int raw : event.getRawSlots()) {
            if (raw >= EDITABLE && raw < SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    void close(Player player) {
        if (finished) {
            return;
        }
        finished = true;
        if (!discard) {
            Map<Integer, ItemStack> contents = contents();
            editor.setContents(kit, contents);
            Messages.send(player, "kits.captured", Mini.value("id", kit),
                    Mini.value("amount", String.valueOf(contents.size())));
        }
        if (back != null) {
            Scheduling.entityLater(player, () -> {
                if (player.isOnline()) {
                    back.run();
                }
            }, 1L);
        }
    }

    Map<Integer, ItemStack> contents() {
        Map<Integer, ItemStack> contents = new HashMap<>();
        for (int slot = 0; slot < EDITABLE; slot++) {
            ItemStack item = inventory.getItem(slot);
            int kitSlot = KitSlots.kitSlot(slot);
            if (item != null && !item.getType().isAir() && kitSlot >= 0) {
                contents.put(kitSlot, item.clone());
            }
        }
        return contents;
    }

    private void capture(PlayerInventory source) {
        for (int slot = 0; slot < KitSlots.SIZE; slot++) {
            ItemStack item = source.getItem(slot);
            int editorSlot = KitSlots.editorSlot(slot);
            if (editorSlot >= 0) {
                inventory.setItem(editorSlot, item == null || item.getType().isAir() ? null : item.clone());
            }
        }
    }

    static Map<Integer, ItemStack> snapshot(PlayerInventory source) {
        Map<Integer, ItemStack> contents = new HashMap<>();
        for (int slot = 0; slot < KitSlots.SIZE; slot++) {
            ItemStack item = source.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                contents.put(slot, item.clone());
            }
        }
        return contents;
    }

    private int firstFree() {
        for (int slot = 0; slot < EDITABLE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }
}
