package com.kirugoldzzzz.loadout;

import com.foliagui.gui.Gui;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class KitHistoryMenu {

    static final int ROWS = 6;
    static final int PAGE = 45;

    private final KitJournal audit;

    public KitHistoryMenu(KitJournal audit) {
        this.audit = audit;
    }

    public void open(Player player, int page, Runnable back) {
        int safe = Math.max(0, page);
        Scheduling.async(() -> {
            List<KitJournal.Entry> entries = audit.page(player.getUniqueId(), PAGE + 1, safe * PAGE);
            Scheduling.entity(player, () -> render(player, safe, entries, back));
        });
    }

    private void render(Player player, int page, List<KitJournal.Entry> loaded, Runnable back) {
        if (!player.isOnline()) {
            return;
        }
        boolean more = loaded.size() > PAGE;
        List<KitJournal.Entry> entries = more ? loaded.subList(0, PAGE) : loaded;
        Gui gui = Gui.builder().rows(ROWS).title(Mini.parse(Palette.smallTitle("Historique des kits"))).create();
        Guis.bottomBar(gui);
        for (int index = 0; index < entries.size(); index++) {
            KitJournal.Entry entry = entries.get(index);
            Material icon = switch (entry.action()) {
                case "Kit offert" -> Material.CAKE;
                case "Argent de kit" -> Material.GOLD_NUGGET;
                case "Bons de kit remis" -> Material.PAPER;
                default -> Material.CHEST_MINECART;
            };
            Card card = Card.of(KitStyle.HEX).tag(entry.action()).blank()
                    .stat(Card.TIME, "Quand", entry.formattedDate() + Palette.MUTED + ", il y a "
                            + Numbers.duration(entry.age()));
            if (entry.hasAmount()) {
                card.money("Montant", Math.abs(entry.amount()));
            }
            if (entry.involvesTwoParties()) {
                card.stat(Card.PLAYER, player.getUniqueId().equals(entry.actor()) ? "Pour" : "De",
                        Mini.escape(player.getUniqueId().equals(entry.actor()) ? entry.subjectName() : entry.actorName()));
            }
            for (String line : detail(entry.detail())) {
                card.line(Mini.escape(line));
            }
            gui.setItem(index, Guis.display(icon, Palette.heading(entry.action()), card.build()));
        }
        if (entries.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB, Palette.MUTED + "<b>Aucun historique</b>",
                    Card.of(KitStyle.HEX).blank().line("Vos récupérations de kits").line("apparaîtront ici.").build()));
        }
        if (back != null) {
            gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        }
        if (page > 0) {
            gui.setItem(ROWS, Guis.PREVIOUS_SLOT, Guis.button(Material.ARROW, Palette.ACCENT + Palette.BACK
                    + " Page précédente", List.of(), viewer -> open(viewer, page - 1, back)));
        }
        if (more) {
            gui.setItem(ROWS, Guis.NEXT_SLOT, Guis.button(Material.ARROW, Palette.ACCENT + "Page suivante "
                    + Palette.POINTER, List.of(), viewer -> open(viewer, page + 1, back)));
        }
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
    }

    static List<String> detail(String detail) {
        if (detail == null || detail.isBlank()) {
            return List.of();
        }
        return List.of(detail.split(" · "));
    }
}
