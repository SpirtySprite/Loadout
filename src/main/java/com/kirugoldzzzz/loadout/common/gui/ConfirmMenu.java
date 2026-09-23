package com.kirugoldzzzz.loadout.common.gui;

import com.foliagui.builder.item.ItemBuilder;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.loadout.common.text.Lore;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ConfirmMenu {

    private static final int CANCEL_COLUMN = 1;
    private static final int SUBJECT_COLUMN = 5;
    private static final int CONFIRM_COLUMN = 9;

    private final String title;

    private ItemStack subject = new ItemStack(Material.PAPER);
    private String question = "Confirmer cette action ?";
    private String confirmLabel = "Confirmer";
    private List<String> details = List.of();
    private Consumer<Player> onConfirm = viewer -> {
    };
    private Consumer<Player> onCancel = Player::closeInventory;

    private ConfirmMenu(String title) {
        this.title = title;
    }

    public static ConfirmMenu create(String title) {
        return new ConfirmMenu(title);
    }

    public ConfirmMenu subject(ItemStack item) {
        this.subject = item == null ? new ItemStack(Material.PAPER) : item.clone();
        return this;
    }

    public ConfirmMenu subject(Material material) {
        return subject(new ItemStack(material));
    }

    public ConfirmMenu question(String value) {
        this.question = value;
        return this;
    }

    public ConfirmMenu details(List<String> lines) {
        this.details = lines == null ? List.of() : List.copyOf(lines);
        return this;
    }

    public ConfirmMenu confirmLabel(String value) {
        this.confirmLabel = value;
        return this;
    }

    public ConfirmMenu onConfirm(Consumer<Player> action) {
        this.onConfirm = action;
        return this;
    }

    public ConfirmMenu onCancel(Consumer<Player> action) {
        this.onCancel = action;
        return this;
    }

    public void open(Player player) {
        AtomicBoolean answered = new AtomicBoolean();
        Gui gui = Gui.builder()
                .rows(1)
                .title(Mini.parse(Palette.title(title)))
                .create();

        for (int column = CANCEL_COLUMN + 1; column < CONFIRM_COLUMN; column++) {
            gui.setItem(1, column, Guis.filler());
        }
        gui.setItem(1, SUBJECT_COLUMN, subjectIcon());
        gui.setItem(1, CANCEL_COLUMN, cancelButton(answered));
        gui.setItem(1, CONFIRM_COLUMN, confirmButton(answered));
        gui.open(player);
    }

    private GuiItem subjectIcon() {
        Lore lore = Lore.create()
                .blank()
                .lines(details)
                .blank()
                .warn(question);
        return ItemBuilder.of(Guis.described(subject, null, lore.build()))
                .asGuiItem(event -> event.setCancelled(true));
    }

    private GuiItem cancelButton(AtomicBoolean answered) {
        return Guis.item(Material.RED_STAINED_GLASS_PANE,
                Palette.DANGER + "<b>Annuler</b>",
                Lore.create()
                        .blank()
                        .text("Revenir sans rien changer.")
                        .build(),
                event -> {
                    Player viewer = (Player) event.getWhoClicked();
                    if (!answered.compareAndSet(false, true)) {
                        return;
                    }
                    Guis.click(viewer);
                    onCancel.accept(viewer);
                });
    }

    private GuiItem confirmButton(AtomicBoolean answered) {
        return Guis.item(Material.LIME_STAINED_GLASS_PANE,
                Palette.SUCCESS + "<b>" + confirmLabel + "</b>",
                Lore.create()
                        .blank()
                        .action("Cliquer pour confirmer")
                        .build(),
                event -> {
                    Player viewer = (Player) event.getWhoClicked();
                    if (!answered.compareAndSet(false, true)) {
                        return;
                    }
                    Guis.click(viewer);
                    onConfirm.accept(viewer);
                });
    }
}
