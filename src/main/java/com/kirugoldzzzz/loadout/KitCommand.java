package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Tr;

import com.kirugoldzzzz.loadout.common.command.NexusCommand;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.Wallet;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class KitCommand extends NexusCommand {

    public static final String ADMIN = "loadout.admin.kits";
    static final String GIFT = "loadout.kit.gift";
    private static final List<String> PLAYER_ACTIONS_FR = List.of("apercu", "offrir", "aide", "collection",
            "historique", "tout", "maitrise", "essayer");
    private static final List<String> ADMIN_ACTIONS_FR = List.of("admin", "creer", "editer", "capturer", "supprimer",
            "donner", "bon", "reset", "joueur", "stock", "recharger", "liste", "ceremonie");
    private static final List<String> PLAYER_ACTIONS_EN = List.of("preview", "gift", "help", "collection",
            "history", "all", "mastery", "tryon");
    private static final List<String> ADMIN_ACTIONS_EN = List.of("admin", "create", "edit", "capture", "delete",
            "give", "voucher", "reset", "player", "stock", "reload", "list", "ceremony");
    private static final List<String> CEREMONY_LEVELS = List.of("0", "1", "2", "3", "4");
    private static final int VOUCHER_LIMIT = 256;

    private final KitActions actions;
    private final KitMenu menu;
    private final KitPreviewMenu preview;
    private final KitAdminMenu admin;
    private final KitEditor editor;
    private final Wallet economy;
    private KitCollectionMenu collection;
    private KitHistoryMenu history;
    private KitMasteryMenu mastery;
    private Runnable reloadSettings = () -> {
    };

    public void bind(KitCollectionMenu collectionMenu, KitHistoryMenu historyMenu, KitMasteryMenu masteryMenu) {
        this.collection = collectionMenu;
        this.history = historyMenu;
        this.mastery = masteryMenu;
    }

    public void onReload(Runnable action) {
        this.reloadSettings = action;
    }

    public KitCommand(KitActions actions, KitMenu menu, KitPreviewMenu preview, KitAdminMenu admin, KitEditor editor,
                      Wallet economy) {
        super("loadout.kit.use", false);
        this.actions = actions;
        this.menu = menu;
        this.preview = preview;
        this.admin = admin;
        this.editor = editor;
        this.economy = economy;
    }

    private KitService service() {
        return actions.service();
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (args.length == 0) {
            if (player == null) {
                Messages.send(sender, sender.hasPermission(ADMIN) ? "kits.admin-usage" : "kits.usage");
                return;
            }
            menu.open(player);
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "aide", "help" -> {
                Messages.send(sender, "kits.usage");
                if (sender.hasPermission(ADMIN)) {
                    Messages.send(sender, "kits.admin-usage");
                }
            }
            case "apercu", "aperçu", "preview", "voir" -> previewKit(sender, player, args);
            case "offrir", "gift" -> gift(sender, player, args);
            case "collection" -> {
                if (requirePlayer(sender, player) && collection != null) {
                    collection.open(player, null);
                }
            }
            case "historique", "history" -> {
                if (requirePlayer(sender, player) && history != null) {
                    history.open(player, 0, null);
                }
            }
            case "tout", "all" -> {
                if (requirePlayer(sender, player) && service().claimAll(player) == 0) {
                    Messages.send(player, "kits.nothing-ready");
                }
            }
            case "maitrise", "maîtrise", "mastery" -> {
                if (requirePlayer(sender, player) && mastery != null) {
                    withKit(sender, args, 1, kit -> mastery.open(player, kit, null));
                }
            }
            case "essayer", "tryon" -> {
                if (requirePlayer(sender, player)) {
                    withKit(sender, args, 1, kit -> KitTryOn.show(player, kit));
                }
            }
            case "admin", "creer", "create", "editer", "edit", "capturer", "capture", "supprimer", "delete", "donner",
                 "give", "bon", "voucher", "reset", "joueur", "player", "stock", "recharger", "reload", "liste",
                 "list", "ceremonie", "cérémonie", "ceremony" -> adminAction(sender, player, action, args);
            default -> claim(sender, player, args[0]);
        }
    }

    private void claim(CommandSender sender, Player player, String id) {
        if (player == null) {
            Messages.send(sender, "general.players-only");
            return;
        }
        Optional<Kit> kit = service().kit(id);
        if (kit.isEmpty()) {
            Optional<KitCategory> category = service().catalog().category(id);
            if (category.isPresent()) {
                menu.open(player, category.get().id());
                return;
            }
            Messages.send(player, "kits.unknown", Mini.value("id", id));
            return;
        }
        KitStatus status = service().status(player, kit.get());
        if (status.state() == KitStatus.State.LOCKED && !service().visible(status, kit.get())) {
            Messages.send(player, "kits.unknown", Mini.value("id", id));
            return;
        }
        actions.request(player, kit.get(), KitService.Source.COMMAND, null);
    }

    private void previewKit(CommandSender sender, Player player, String[] args) {
        if (player == null) {
            Messages.send(sender, "general.players-only");
            return;
        }
        if (args.length < 2) {
            Messages.send(player, "kits.usage");
            return;
        }
        Optional<Kit> kit = service().kit(args[1]);
        if (kit.isEmpty()) {
            Messages.send(player, "kits.unknown", Mini.value("id", args[1]));
            return;
        }
        preview.open(player, kit.get(), null);
    }

    private void gift(CommandSender sender, Player player, String[] args) {
        if (player == null) {
            Messages.send(sender, "general.players-only");
            return;
        }
        if (!player.hasPermission(GIFT)) {
            Messages.send(player, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            Messages.send(player, "kits.usage");
            return;
        }
        Player receiver = Bukkit.getPlayerExact(args[1]);
        if (receiver == null) {
            Messages.send(player, "kits.offline");
            return;
        }
        Optional<Kit> kit = service().kit(args[2]);
        if (kit.isEmpty()) {
            Messages.send(player, "kits.unknown", Mini.value("id", args[2]));
            return;
        }
        KitStatus status = service().status(player, kit.get());
        if (!status.available()) {
            actions.explain(player, kit.get(), status);
            return;
        }
        actions.gift(player, receiver, kit.get());
    }

    private void adminAction(CommandSender sender, Player player, String action, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            if (args.length == 1 && player != null) {
                claim(sender, player, args[0]);
            } else {
                Messages.send(sender, "general.no-permission");
            }
            return;
        }
        switch (action) {
            case "admin" -> {
                if (requirePlayer(sender, player)) {
                    admin.open(player);
                }
            }
            case "creer", "create" -> create(sender, player, args);
            case "editer", "edit" -> withKit(sender, args, 1, kit -> {
                if (requirePlayer(sender, player)) {
                    admin.edit(player, kit.id(), null);
                }
            });
            case "capturer", "capture" -> withKit(sender, args, 1, kit -> {
                if (requirePlayer(sender, player)) {
                    int captured = admin.capture(player, kit.id());
                    Messages.send(player, "kits.captured", Mini.value("id", kit.id()),
                            Mini.value("amount", String.valueOf(captured)));
                }
            });
            case "supprimer", "delete" -> withKit(sender, args, 1, kit -> {
                editor.delete(kit.id());
                service().repository().forget(kit.id());
                Messages.send(sender, "kits.deleted", Mini.value("id", kit.id()));
            });
            case "donner", "give" -> give(sender, args);
            case "bon", "voucher" -> voucher(sender, args);
            case "reset" -> reset(sender, args);
            case "joueur", "player" -> {
                if (requirePlayer(sender, player) && args.length > 1) {
                    Optional<UUID> target = economy.resolve(args[1]);
                    if (target.isEmpty()) {
                        Messages.send(sender, "general.unknown-player", Mini.value("player", args[1]));
                        return;
                    }
                    admin.player(player, target.get(), null);
                } else if (args.length <= 1) {
                    Messages.send(sender, "kits.admin-usage");
                }
            }
            case "stock" -> withKit(sender, args, 1, kit -> {
                service().repository().resetStock(kit.id());
                Messages.send(sender, "kits.stock-reset", KitService.kitResolver(kit));
            });
            case "recharger", "reload" -> {
                reloadSettings.run();
                editor.reload();
                Messages.send(sender, "kits.reloaded",
                        Mini.value("amount", String.valueOf(service().catalog().kits().size())));
                int problems = service().catalog().problems().size();
                if (problems > 0) {
                    Messages.send(sender, "kits.reload-problems", Mini.value("amount", String.valueOf(problems)));
                }
            }
            case "ceremonie", "cérémonie", "ceremony" -> ceremony(sender, player, args);
            case "liste", "list" -> {
                if (player != null) {
                    admin.open(player);
                    return;
                }
                sender.sendMessage(String.join(", ", service().catalog().kits().keySet()));
            }
            default -> Messages.send(sender, "kits.admin-usage");
        }
    }

    private void ceremony(CommandSender sender, Player player, String[] args) {
        if (!requirePlayer(sender, player)) {
            return;
        }
        int level = KitShow.MAXIMUM;
        if (args.length > 1) {
            level = Numbers.parseInt(args[1], Integer.MIN_VALUE);
            if (level < 0 || level > KitShow.MAXIMUM) {
                Messages.send(player, "kits.invalid-value", Mini.value("input", args[1]));
                return;
            }
        }
        Kit kit = args.length > 2 ? service().kit(args[2]).orElse(null) : null;
        if (args.length > 2 && kit == null) {
            Messages.send(player, "kits.unknown", Mini.value("id", args[2]));
            return;
        }
        actions.ceremony(player, level, kit);
    }

    private static boolean requirePlayer(CommandSender sender, Player player) {
        if (player == null) {
            Messages.send(sender, "general.players-only");
            return false;
        }
        return true;
    }

    private void withKit(CommandSender sender, String[] args, int index, java.util.function.Consumer<Kit> action) {
        if (args.length <= index) {
            Messages.send(sender, "kits.admin-usage");
            return;
        }
        Optional<Kit> kit = service().kit(args[index]);
        if (kit.isEmpty()) {
            Messages.send(sender, "kits.unknown", Mini.value("id", args[index]));
            return;
        }
        action.accept(kit.get());
    }

    private void create(CommandSender sender, Player player, String[] args) {
        if (!requirePlayer(sender, player)) {
            return;
        }
        if (args.length < 2 || !KitLoader.ID.matcher(KitEditor.slug(args[1])).matches()) {
            Messages.send(player, "kits.invalid-id");
            return;
        }
        String id = editor.create(args[1], player.getInventory().getItemInMainHand());
        Messages.send(player, "kits.created", Mini.value("id", id));
        admin.edit(player, id, null);
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "kits.admin-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Messages.send(sender, "kits.offline");
            return;
        }
        withKit(sender, args, 2, kit -> Scheduling.entity(target, () -> {
            KitService.Outcome outcome = service().claim(target, kit, KitService.Source.ADMIN);
            if (outcome.success()) {
                Messages.send(sender, "kits.given", KitService.kitResolver(kit), Mini.value("player", target.getName()));
            } else {
                Messages.send(sender, "kits.give-failed", KitService.kitResolver(kit),
                        Mini.value("player", target.getName()));
            }
        }));
    }

    private void voucher(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "kits.admin-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Messages.send(sender, "kits.offline");
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException invalid) {
                amount = -1;
            }
        }
        if (amount < 1 || amount > VOUCHER_LIMIT) {
            Messages.send(sender, "kits.invalid-value", Mini.value("input", args.length > 3 ? args[3] : "?"));
            return;
        }
        int given = amount;
        withKit(sender, args, 2, kit -> {
            service().giveVouchers(target, kit, given, sender.getName());
            Messages.send(sender, "kits.vouchers-given", KitService.kitResolver(kit),
                    Mini.value("amount", String.valueOf(given)), Mini.value("player", target.getName()));
        });
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Messages.send(sender, "kits.admin-usage");
            return;
        }
        Optional<UUID> target = economy.resolve(args[1]);
        if (target.isEmpty()) {
            Messages.send(sender, "general.unknown-player", Mini.value("player", args[1]));
            return;
        }
        String kit = null;
        if (args.length > 2 && !args[2].equalsIgnoreCase("tout") && !args[2].equalsIgnoreCase("all")) {
            Optional<Kit> found = service().kit(args[2]);
            if (found.isEmpty()) {
                Messages.send(sender, "kits.unknown", Mini.value("id", args[2]));
                return;
            }
            kit = found.get().id();
        }
        int removed = service().reset(target.get(), kit);
        Messages.send(sender, "kits.reset", Mini.value("count", String.valueOf(removed)),
                Mini.value("player", economy.nameOf(target.get())));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        List<String> kits = new ArrayList<>(service().catalog().kits().keySet());
        if (args.length == 1) {
            List<String> options = new ArrayList<>(visibleKits(sender));
            boolean french = "fr".equals(Tr.language());
            options.addAll(french ? PLAYER_ACTIONS_FR : PLAYER_ACTIONS_EN);
            if (admin) {
                options.addAll(french ? ADMIN_ACTIONS_FR : ADMIN_ACTIONS_EN);
            }
            return match(options, args[0]);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        List<String> online = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 2) {
            return switch (action) {
                case "apercu", "preview", "voir", "maitrise", "mastery", "essayer", "tryon" ->
                        match(visibleKits(sender), args[1]);
                case "offrir", "gift" -> match(online, args[1]);
                case "editer", "edit", "capturer", "capture", "supprimer", "delete", "stock" ->
                        admin ? match(kits, args[1]) : List.of();
                case "donner", "give", "bon", "voucher", "reset", "joueur", "player" ->
                        admin ? match(online, args[1]) : List.of();
                case "ceremonie", "cérémonie", "ceremony" -> admin ? match(CEREMONY_LEVELS, args[1]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (action) {
                case "offrir", "gift" -> match(service().catalog().kits().values().stream()
                        .filter(kit -> kit.options().giftable()).map(Kit::id).toList(), args[2]);
                case "donner", "give", "bon", "voucher" -> admin ? match(kits, args[2]) : List.of();
                case "ceremonie", "cérémonie", "ceremony" -> admin ? match(kits, args[2]) : List.of();
                case "reset" -> {
                    List<String> options = new ArrayList<>(kits);
                    options.add("tout");
                    yield admin ? match(options, args[2]) : List.of();
                }
                default -> List.of();
            };
        }
        if (args.length == 4 && admin && (action.equals("bon") || action.equals("voucher"))) {
            return match(List.of("1", "5", "10", "64"), args[3]);
        }
        return List.of();
    }

    private List<String> visibleKits(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>(service().catalog().kits().keySet());
        }
        List<String> ids = new ArrayList<>();
        for (Kit kit : service().catalog().kits().values()) {
            if (kit.permission() == null || player.hasPermission(kit.permission()) || !kit.hideLocked()) {
                ids.add(kit.id());
            }
        }
        return ids;
    }
}
