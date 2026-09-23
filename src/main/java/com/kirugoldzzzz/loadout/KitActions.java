package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.gui.ChatPrompts;
import com.kirugoldzzzz.loadout.common.gui.ConfirmMenu;
import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.text.Card;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Palette;
import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class KitActions {

    private final KitService service;
    private final CrateBridge crates;

    public KitActions(KitService service, CrateBridge crates) {
        this.service = service;
        this.crates = crates;
    }

    public KitService service() {
        return service;
    }

    public String crateName(String id) {
        return crates.crate(id).map(CrateBridge.KeyCrate::displayName).map(name -> Mini.plain(Mini.label(name))).orElse(id);
    }

    public long lastClaim(Player player, Kit kit) {
        KitClaim claim = service.repository().find(player.getUniqueId(), kit.id());
        return claim == null ? 0L : claim.last();
    }

    public List<String> card(Player player, Kit kit, KitStatus status, boolean clicks) {
        return card(player, service.viewer(player), kit, status, clicks);
    }

    public List<String> card(Player player, KitViewer viewer, Kit kit, KitStatus status, boolean clicks) {
        long now = System.currentTimeMillis();
        KitStyle.Extras extras = new KitStyle.Extras(service.progress(viewer, kit, now), service.discount(kit, now),
                service.repository().favorites(player.getUniqueId()).contains(kit.id()), kit.options().team());
        KitClaim claim = service.repository().find(viewer.owner(kit), kit.id());
        return KitStyle.card(kit, status, service.catalog(), now, claim == null ? 0L : claim.last(), this::crateName,
                clicks, extras);
    }

    public ItemStack icon(Player player, KitViewer viewer, Kit kit, KitStatus status, boolean clicks) {
        boolean favorite = service.repository().favorites(player.getUniqueId()).contains(kit.id());
        String name = (favorite ? "<#FBBF24>★ " : "") + kit.name();
        return KitStyle.decorate(KitStyle.icon(kit), name, card(player, viewer, kit, status, clicks), status.available());
    }

    public ItemStack icon(Player player, Kit kit, KitStatus status, boolean clicks) {
        return KitStyle.decorate(KitStyle.icon(kit), kit.name(), card(player, kit, status, clicks), status.available());
    }

    public void request(Player player, Kit kit, KitService.Source source, Runnable after) {
        KitStatus status = service.status(player, kit);
        if (!status.available()) {
            Guis.deny(player);
            explain(player, kit, status);
            return;
        }
        KitCost cost = service.cost(kit, System.currentTimeMillis());
        if (kit.options().confirm() && !cost.free() && !player.hasPermission(KitViewer.BYPASS_COST)) {
            ConfirmMenu.create(Tr.t("Acheter un kit"))
                    .subject(KitStyle.decorate(KitStyle.icon(kit), kit.name(), List.of(), true))
                    .question(Tr.t("Récupérer ce kit ?"))
                    .confirmLabel(Tr.t("Payer et récupérer"))
                    .details(costLines(cost))
                    .onConfirm(viewer -> {
                        viewer.closeInventory();
                        run(viewer, kit, source);
                        if (after != null) {
                            after.run();
                        }
                    })
                    .onCancel(viewer -> {
                        if (after != null) {
                            after.run();
                        } else {
                            viewer.closeInventory();
                        }
                    })
                    .open(player);
            return;
        }
        run(player, kit, source);
        if (after != null) {
            after.run();
        }
    }

    private static List<String> costLines(KitCost cost) {
        Card card = Card.of(KitStyle.HEX).blank();
        if (cost.money() > 0.0D) {
            card.money(Tr.t("Prix"), cost.money());
        }
        if (cost.shards() > 0L) {
            card.stat(Card.STAR, Tr.t("Fragments"), Numbers.count(cost.shards()));
        }
        if (cost.levels() > 0) {
            card.stat(Card.STAR, Tr.t("Niveaux"), String.valueOf(cost.levels()));
        }
        return card.build();
    }

    public KitService.Outcome run(Player player, Kit kit, KitService.Source source) {
        KitService.Outcome outcome = service.claim(player, kit, source);
        report(player, outcome);
        if (outcome.success() && !outcome.roulette().isEmpty()) {
            KitRoulette.open(service, player, kit, outcome.roulette());
        }
        return outcome;
    }

    public void report(Player player, KitService.Outcome outcome) {
        switch (outcome.result()) {
            case SUCCESS -> Guis.success(player);
            case BUSY -> Messages.send(player, "kits.busy");
            case FAILED -> {
                Guis.deny(player);
                Messages.send(player, "empty".equals(outcome.reason()) ? "kits.empty" : "kits.failed",
                        KitService.kitResolver(outcome.kit()));
            }
            case REFUSED -> {
                Guis.deny(player);
                if ("mastery-max".equals(outcome.reason()) || "mastery-locked".equals(outcome.reason())) {
                    Messages.send(player, "kits.reason." + outcome.reason());
                    return;
                }
                if (outcome.reason() != null && Messages.has("kits.reason." + outcome.reason())) {
                    Messages.send(player, "kits.reason." + outcome.reason());
                } else if (outcome.status() != null) {
                    explain(player, outcome.kit(), outcome.status());
                }
            }
        }
    }

    public void explain(Player player, Kit kit, KitStatus status) {
        String key = switch (status.state()) {
            case AVAILABLE -> null;
            case COOLDOWN -> "kits.refused.cooldown";
            case EXHAUSTED -> "kits.refused.exhausted";
            case SOLD_OUT -> "kits.refused.sold-out";
            case LOCKED -> "kits.refused.locked";
            case CLOSED -> "kits.refused.closed";
            case REQUIREMENTS -> "kits.refused.requirements";
            case UNAFFORDABLE -> "kits.refused.unaffordable";
        };
        if (key == null) {
            return;
        }
        List<String> missing = new ArrayList<>();
        String schedule = "";
        for (KitStatus.Check check : status.checks()) {
            if (!check.met()) {
                missing.add(check.label() + " " + check.detail());
                if (check.kind() == KitStatus.Kind.SCHEDULE) {
                    schedule = check.detail();
                }
            }
        }
        String detail = status.state() == KitStatus.State.CLOSED ? schedule : String.join(", ", missing);
        Messages.send(player, key, KitService.kitResolver(kit),
                Mini.value("time", Numbers.duration(status.remaining())),
                Mini.value("detail", detail),
                Mini.value("hint", String.join(" ", kit.hint())));
    }

    public void promptGift(Player player, Kit kit, Runnable back) {
        if (!kit.options().giftable()) {
            Guis.deny(player);
            Messages.send(player, "kits.reason.not-giftable");
            return;
        }
        KitStatus status = service.status(player, kit);
        if (!status.available()) {
            Guis.deny(player);
            explain(player, kit, status);
            return;
        }
        Messages.send(player, "kits.gift-prompt", KitService.kitResolver(kit));
        ChatPrompts.open(player, Tr.t("le pseudo du joueur"), typed -> {
            Player receiver = typed.isBlank() ? null : Bukkit.getPlayerExact(typed.trim());
            if (receiver == null) {
                Guis.deny(player);
                Messages.send(player, "kits.offline");
                if (back != null) {
                    back.run();
                }
                return;
            }
            confirmGift(player, receiver, kit, back);
        });
    }

    private void confirmGift(Player player, Player receiver, Kit kit, Runnable back) {
        List<String> details = new ArrayList<>(Card.of(KitStyle.HEX).blank()
                .stat(Card.PLAYER, Tr.t("Destinataire"), receiver.getName())
                .line(Tr.t("Il recevra un bon à ouvrir quand il veut."))
                .line(Tr.t("Votre propre recharge démarre maintenant."))
                .build());
        details.addAll(costLines(service.cost(kit, System.currentTimeMillis())));
        ConfirmMenu.create(Tr.t("Offrir un kit"))
                .subject(KitStyle.decorate(KitStyle.icon(kit), kit.name(), List.of(), true))
                .question(Tr.t("Offrir ce kit à ") + receiver.getName() + " ?")
                .confirmLabel(Tr.t("Offrir"))
                .details(details)
                .onConfirm(viewer -> {
                    viewer.closeInventory();
                    gift(viewer, receiver, kit);
                    if (back != null) {
                        back.run();
                    }
                })
                .onCancel(viewer -> {
                    if (back != null) {
                        back.run();
                    } else {
                        viewer.closeInventory();
                    }
                })
                .open(player);
    }

    public void gift(Player giver, Player receiver, Kit kit) {
        KitService.Outcome outcome = service.gift(giver, receiver, kit);
        if (!outcome.success()) {
            report(giver, outcome);
            return;
        }
        Guis.success(giver);
        Messages.send(giver, "kits.gift-sent", KitService.kitResolver(kit), Mini.value("player", receiver.getName()));
        Messages.send(receiver, "kits.gift-received", KitService.kitResolver(kit),
                Mini.value("player", giver.getName()));
    }

    public void redeem(Player player, ItemStack held) {
        String id = KitItems.voucherKit(held);
        if (id == null) {
            return;
        }
        Optional<Kit> kit = service.kit(id);
        if (kit.isEmpty()) {
            Guis.deny(player);
            Messages.send(player, "kits.voucher-unknown");
            return;
        }
        run(player, kit.get(), KitService.Source.VOUCHER);
    }

    public void ceremony(Player player, int level, Kit kit) {
        KitShow show = KitShow.of(level);
        List<ItemStack> cast = new ArrayList<>();
        if (kit != null) {
            cast.addAll(kit.contents().values());
            for (KitPool.Entry entry : kit.pool().entries()) {
                if (entry.item() != null) {
                    cast.add(entry.item());
                }
            }
        }
        if (cast.isEmpty()) {
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (item != null && !item.getType().isAir()) {
                    cast.add(item);
                }
            }
        }
        Kit shown = kit == null ? demo(player, show) : kit;
        KitUnboxing.play(player, shown, cast, show);
        Messages.send(player, "kits.ceremony-played", Mini.value("level", String.valueOf(show.level())),
                Mini.value("name", show.name()), Mini.value("time", Numbers.duration(show.duration() * 50L)));
    }

    private static Kit demo(Player player, KitShow show) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack icon = held.getType().isAir() ? new ItemStack(Material.CHEST) : held.clone();
        icon.setAmount(1);
        return new Kit("apercu", Tr.t("<#A78BFA><b>Cérémonie ") + show.name() + "</b>", List.of(), icon, null, 0, null, 0L,
                KitReset.NONE, LocalTime.MIDNIGHT, DayOfWeek.MONDAY, 0, 0, KitCost.FREE, KitRequirements.NONE,
                Map.of(), KitOptions.DEFAULTS, Map.of(), KitPool.EMPTY, KitRewards.NONE, false, List.of(),
                KitMastery.NONE, 0L, show.level());
    }

    public String label(Kit kit) {
        return Palette.TEXT + KitService.plain(kit);
    }
}
