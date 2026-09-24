package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.api.event.KitClaimEvent;
import com.kirugoldzzzz.loadout.api.event.KitClaimedEvent;
import com.kirugoldzzzz.loadout.common.effect.Particles;
import com.kirugoldzzzz.loadout.common.item.Inventories;
import com.kirugoldzzzz.loadout.common.item.ItemReturn;
import com.kirugoldzzzz.loadout.common.log.LogTopic;
import com.kirugoldzzzz.loadout.common.log.PluginLog;
import com.kirugoldzzzz.loadout.common.scheduler.Scheduling;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import com.kirugoldzzzz.loadout.common.text.Tr;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;

public final class KitService {

    static final long MILLIS_PER_TICK = 50L;
    static final long REMINDER_CHECK_SECONDS = 5L;
    static final int SLOW_CHECK_EVERY = 12;
    static final int PREVIEW_LIMIT = 14;
    static final String COLLECTION_MARK = "collection:";
    static final String KIT_MARK = "kit:";

    public enum Source {
        MENU(true, true, true),
        COMMAND(true, true, true),
        VOUCHER(false, false, true),
        GIFT(true, true, true),
        FIRST_JOIN(false, true, false),
        RESPAWN(true, true, false),
        BULK(true, true, false),
        ADMIN(false, false, true);

        private final boolean charge;
        private final boolean record;
        private final boolean feedback;

        Source(boolean charge, boolean record, boolean feedback) {
            this.charge = charge;
            this.record = record;
            this.feedback = feedback;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    public enum Result {
        SUCCESS,
        REFUSED,
        BUSY,
        FAILED
    }

    public record Outcome(Result result, Kit kit, KitStatus status, int stashed, List<ItemStack> items, String reason,
                          List<KitPool.Pull> roulette) {

        public boolean success() {
            return result == Result.SUCCESS;
        }

        static Outcome of(Result result, Kit kit, KitStatus status, String reason) {
            return new Outcome(result, kit, status, 0, List.of(), reason, List.of());
        }
    }

    public record Claimed(Player player, Kit kit, List<ItemStack> items, int masteryLevel) {
    }

    public record Progress(Kit kit, int level, KitMastery mastery, int uses, int streak, int best, int bonus) {
    }

    private final KitRepository repository;
    private final Wallet economy;
    private final CrateBridge crates;
    private final PlayerSettingsRepository playerSettings;
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastClaim = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> reminders = new ConcurrentHashMap<>();
    private final Map<String, Boolean> openKits = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private volatile KitCatalog catalog = KitCatalog.EMPTY;
    private volatile Consumer<Claimed> animator = ignored -> {
    };
    private volatile Function<UUID, UUID> teams = ignored -> null;
    private volatile ScheduledTask reminderTask;
    private int ticks;

    public KitService(KitRepository repository, Wallet economy, CrateBridge crates,
                      PlayerSettingsRepository playerSettings) {
        this.repository = repository;
        this.economy = economy;
        this.crates = crates;
        this.playerSettings = playerSettings;
    }

    public void configure(ConfigurationSection root) {
        crates.configure(root == null ? null : root.getConfigurationSection("crate-keys"));
        KitCatalog loaded = KitLoader.read(root);
        for (String problem : loaded.problems()) {
            PluginLog.warn(LogTopic.KITS, problem);
        }
        catalog = loaded;
    }

    public void animator(Consumer<Claimed> value) {
        animator = value == null ? ignored -> {
        } : value;
    }

    public void teams(Function<UUID, UUID> lookup) {
        teams = lookup == null ? ignored -> null : lookup;
    }

    public KitCatalog catalog() {
        return catalog;
    }

    public KitSettings settings() {
        return catalog.settings();
    }

    public Optional<Kit> kit(String id) {
        return catalog.kit(id);
    }

    public KitRepository repository() {
        return repository;
    }

    public CrateBridge crates() {
        return crates;
    }

    public void start() {
        stop();
        reminderTask = Scheduling.asyncTimer(this::tick, REMINDER_CHECK_SECONDS, REMINDER_CHECK_SECONDS);
        for (Player player : Bukkit.getOnlinePlayers()) {
            planReminders(player.getUniqueId());
        }
    }

    public void stop() {
        ScheduledTask task = reminderTask;
        reminderTask = null;
        if (task != null) {
            task.cancel();
        }
    }

    public KitViewer viewer(Player player) {
        long ticksPlayed;
        try {
            ticksPlayed = Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
        } catch (RuntimeException unavailable) {
            ticksPlayed = 0L;
        }
        UUID team = teams.apply(player.getUniqueId());
        return new KitViewer(player.getUniqueId(), player::hasPermission, ticksPlayed * MILLIS_PER_TICK,
                economy.balance(player.getUniqueId()), 0L, player.getLevel(),
                player.getWorld().getName(), team, statistic -> statistic(player, statistic));
    }

    static long statistic(Player player, KitStatistic statistic) {
        try {
            if (statistic.material() != null) {
                return player.getStatistic(statistic.statistic(), statistic.material());
            }
            if (statistic.entity() != null) {
                return player.getStatistic(statistic.statistic(), statistic.entity());
            }
            return player.getStatistic(statistic.statistic());
        } catch (RuntimeException unavailable) {
            return 0L;
        }
    }

    public String featured(long now) {
        KitCatalog current = catalog;
        return current.progression().featured().pick(current.featuredCandidates(), now, current.settings().zone());
    }

    public int discount(Kit kit, long now) {
        return kit.id().equals(featured(now)) ? catalog.progression().featured().discount() : 0;
    }

    public KitCost cost(Kit kit, long now) {
        int discount = discount(kit, now);
        if (discount <= 0) {
            return kit.cost();
        }
        KitCost cost = kit.cost();
        int kept = 100 - discount;
        return new KitCost(Numbers.round(cost.money() * kept / 100.0D), cost.shards() * kept / 100L,
                cost.levels() * kept / 100);
    }

    public Progress progress(KitViewer viewer, Kit kit, long now) {
        KitClaim claim = repository.find(viewer.owner(kit), kit.id());
        KitMastery mastery = catalog.masteryOf(kit);
        int uses = claim == null ? 0 : claim.uses();
        int level = mastery.level(uses, claim == null ? 0 : claim.tier());
        int streak = streaksOn(kit) ? KitProgression.liveStreak(kit, claim, now, settings().zone()) : 0;
        int bonus = mastery.bonus(level) + catalog.progression().streaks().bonus(streak);
        return new Progress(kit, level, mastery, uses, streak, claim == null ? 0 : claim.best(), bonus);
    }

    public boolean streaksOn(Kit kit) {
        return kit.options().streaks() && kit.reset() != KitReset.NONE && catalog.progression().streaks().enabled();
    }

    public KitStatus status(Player player, Kit kit) {
        return status(viewer(player), kit, System.currentTimeMillis());
    }

    public KitStatus status(KitViewer viewer, Kit kit, long now) {
        UUID id = viewer.id();
        UUID owner = viewer.owner(kit);
        KitCatalog current = catalog;
        KitClaim claim = repository.find(owner, kit.id());
        KitMastery mastery = current.masteryOf(kit);
        int level = mastery.level(claim == null ? 0 : claim.uses(), claim == null ? 0 : claim.tier());
        return KitRules.evaluate(kit, viewer, claim, repository.stockUsed(kit.id()), now, current.settings().zone(),
                other -> repository.find(id, other), current::nameOf, cost(kit, now), mastery.reduction(level));
    }

    public boolean visible(KitStatus status, Kit kit) {
        if (status.state() != KitStatus.State.LOCKED) {
            return true;
        }
        return !kit.hideLocked() && settings().showLocked();
    }

    public int availableCount(Player player) {
        KitViewer viewer = viewer(player);
        long now = System.currentTimeMillis();
        int count = 0;
        for (Kit kit : catalog.kits().values()) {
            if (status(viewer, kit, now).available()) {
                count++;
            }
        }
        return count;
    }

    public Set<String> collected(UUID player) {
        Set<String> collected = new HashSet<>();
        for (KitClaim claim : repository.claimsOf(player).values()) {
            if (claim.uses() > 0) {
                collected.add(claim.kit());
            }
        }
        for (String mark : repository.milestones(player)) {
            if (mark.startsWith(KIT_MARK)) {
                collected.add(mark.substring(KIT_MARK.length()));
            }
        }
        collected.retainAll(catalog.kits().keySet());
        return collected;
    }

    public Outcome claim(Player player, Kit kit, Source source) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        KitSettings settings = settings();
        Long previous = lastClaim.get(id);
        if (source.feedback && previous != null && now - previous < settings.claimSpacingMillis()) {
            return Outcome.of(Result.BUSY, kit, null, "spacing");
        }
        if (!claiming.add(id)) {
            return Outcome.of(Result.BUSY, kit, null, "busy");
        }
        try {
            KitViewer viewer = viewer(player);
            KitStatus status = status(viewer, kit, now);
            if (source == Source.VOUCHER && !kit.options().voucherIgnoresLimits() && !status.available()
                    && status.state() != KitStatus.State.UNAFFORDABLE) {
                return Outcome.of(Result.REFUSED, kit, status, null);
            }
            if (requiresChecks(source) && !status.available()
                    && (source != Source.FIRST_JOIN || !firstJoinAllowed(status))) {
                return Outcome.of(Result.REFUSED, kit, status, null);
            }
            if (kit.empty()) {
                return Outcome.of(Result.FAILED, kit, status, "empty");
            }
            UUID owner = viewer.owner(kit);
            KitCost cost = cost(kit, now);
            boolean charge = source.charge && !viewer.has(KitViewer.BYPASS_COST) && !cost.free();
            if (!announce(player, kit, source, charge ? cost.money() : 0.0D)) {
                return Outcome.of(Result.REFUSED, kit, null, "cancelled");
            }
            boolean record = source.record || (source == Source.VOUCHER && !kit.options().voucherIgnoresLimits());
            KitClaim before = repository.find(owner, kit.id());
            KitMastery mastery = catalog.masteryOf(kit);
            int levelBefore = mastery.level(before == null ? 0 : before.uses(), before == null ? 0 : before.tier());
            int streak = record && streaksOn(kit) ? KitProgression.nextStreak(kit, before, now, settings.zone()) : -1;
            int bonus = mastery.bonus(levelBefore) + catalog.progression().streaks().bonus(Math.max(0, streak));
            KitPool pool = kit.pool();
            int extra = mastery.extraRolls(levelBefore);
            if (extra > 0 && !pool.entries().isEmpty()) {
                pool = new KitPool(pool.rolls() + extra, pool.unique(), pool.entries());
            }
            List<KitPool.Pull> pulls = pool.roll(ThreadLocalRandom.current());
            boolean roulette = kit.options().roulette() && !pulls.isEmpty() && source.feedback;
            List<KitPool.Pull> immediate = roulette ? List.of() : pulls;
            List<ItemStack> tierItems = new ArrayList<>();
            KitMastery.Tier tier = mastery.tier(levelBefore);
            if (tier != null) {
                tierItems.addAll(tier.items());
            }
            int[] levelAfter = {levelBefore};
            List<KitRewards> milestoneRewards = new ArrayList<>();
            List<String> milestoneLabels = new ArrayList<>();
            Delivery delivery = repository.change(() -> {
                repository.watchInventory(player);
                if (source == Source.VOUCHER && !KitItems.takeVoucher(player, kit.id())) {
                    throw new Refusal("voucher-missing");
                }
                if (charge) {
                    pay(player, cost);
                }
                if (record) {
                    if (kit.limitedStock()) {
                        if (repository.stockUsed(kit.id()) >= kit.stock()) {
                            throw new Refusal("sold-out");
                        }
                        repository.consumeStock(kit.id());
                    }
                    KitClaim after = repository.record(owner, kit.id(), now, streak);
                    levelAfter[0] = mastery.level(after.uses(), after.tier());
                    if (streak > 0) {
                        KitRewards milestone = catalog.progression().streaks().milestones().get(streak);
                        if (milestone != null) {
                            milestoneRewards.add(milestone);
                            milestoneLabels.add(Tr.t("série de ") + streak);
                        }
                    }
                    repository.grantMilestone(id, KIT_MARK + kit.id());
                    int collected = collected(id).size();
                    for (Map.Entry<Integer, KitRewards> entry : catalog.progression().collection().entrySet()) {
                        if (collected >= entry.getKey()
                                && repository.grantMilestone(id, COLLECTION_MARK + entry.getKey())) {
                            milestoneRewards.add(entry.getValue());
                            milestoneLabels.add(Tr.t("collection de ") + entry.getKey() + Tr.t(" kits"));
                        }
                    }
                }
                Delivery delivered = deliver(player, kit, immediate, tierItems, settings);
                reward(player, kit.rewards(), 100 + bonus);
                for (KitRewards milestone : milestoneRewards) {
                    reward(player, milestone, 100);
                }
                return delivered;
            });
            lastClaim.put(id, now);
            if (roulette) {
                ItemReturn.park(id, rouletteItems(kit, pulls, settings));
            }
            if (Bukkit.getServer() != null) {
                Bukkit.getPluginManager().callEvent(new KitClaimedEvent(player, kit.id(), source.id(),
                        delivery.items(), levelAfter[0]));
            }
            try {
                after(player, kit, source, delivery, charge, cost, bonus, streak, levelBefore, levelAfter[0], mastery,
                        roulette);
                for (int index = 0; index < milestoneRewards.size(); index++) {
                    celebrate(player, milestoneRewards.get(index), milestoneLabels.get(index), kit);
                }
            } catch (RuntimeException failure) {
                PluginLog.warn(LogTopic.KITS, Tr.t("Effets du kit ") + kit.id() + Tr.t(" incomplets pour ") + player.getName(), failure);
            }
            return new Outcome(Result.SUCCESS, kit, status, delivery.stashed(), delivery.items(), null,
                    roulette ? pulls : List.of());
        } catch (Refusal refusal) {
            return Outcome.of(Result.REFUSED, kit, status(player, kit), refusal.getMessage());
        } catch (RuntimeException failure) {
            PluginLog.warn(LogTopic.KITS, Tr.t("Remise du kit ") + kit.id() + Tr.t(" impossible pour ") + player.getName(), failure);
            return Outcome.of(Result.FAILED, kit, null, "failed");
        } finally {
            claiming.remove(id);
        }
    }

    private static boolean announce(Player player, Kit kit, Source source, double price) {
        if (Bukkit.getServer() == null) {
            return true;
        }
        KitClaimEvent event = new KitClaimEvent(player, kit.id(), source.id(), price);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    List<ItemStack> rouletteItems(Kit kit, List<KitPool.Pull> pulls, KitSettings settings) {
        List<ItemStack> items = new ArrayList<>();
        for (KitPool.Pull pull : pulls) {
            if (pull.entry().item() != null) {
                items.addAll(Inventories.split(prepare(pull.entry().item(), kit, settings, System.currentTimeMillis()),
                        pull.amount()));
            }
        }
        return items;
    }

    public void finishRoulette(Player player) {
        List<ItemStack> owed = ItemReturn.claim(player.getUniqueId());
        if (!owed.isEmpty()) {
            ItemReturn.give(player, owed, Tr.t("Roulette de kit"));
        }
    }

    private static boolean requiresChecks(Source source) {
        return source != Source.ADMIN && source != Source.VOUCHER;
    }

    private static boolean firstJoinAllowed(KitStatus status) {
        return switch (status.state()) {
            case AVAILABLE, UNAFFORDABLE, REQUIREMENTS -> true;
            default -> false;
        };
    }

    public int claimAll(Player player) {
        long now = System.currentTimeMillis();
        KitViewer viewer = viewer(player);
        List<Kit> ready = new ArrayList<>();
        for (Kit kit : catalog.kits().values()) {
            if (!kit.options().roulette() && cost(kit, now).free() && status(viewer, kit, now).available()) {
                ready.add(kit);
            }
        }
        int claimed = 0;
        int items = 0;
        int stashed = 0;
        for (Kit kit : ready) {
            Outcome outcome = claim(player, kit, Source.BULK);
            if (outcome.success()) {
                claimed++;
                items += outcome.items().size();
                stashed += outcome.stashed();
            }
        }
        if (claimed > 0) {
            Messages.send(player, "kits.claimed-all", Mini.value("amount", String.valueOf(claimed)),
                    Mini.value("items", String.valueOf(items)));
            if (stashed > 0) {
                Messages.send(player, "kits.claimed-stash", Mini.value("amount", String.valueOf(stashed)));
            }
            if (playerSettings.get(player.getUniqueId()).sounds()) {
                player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
            }
        }
        return claimed;
    }

    public Outcome upgrade(Player player, Kit kit) {
        UUID id = player.getUniqueId();
        if (!claiming.add(id)) {
            return Outcome.of(Result.BUSY, kit, null, "busy");
        }
        try {
            KitViewer viewer = viewer(player);
            UUID owner = viewer.owner(kit);
            KitMastery mastery = catalog.masteryOf(kit);
            KitClaim claim = repository.find(owner, kit.id());
            int level = mastery.level(claim == null ? 0 : claim.uses(), claim == null ? 0 : claim.tier());
            KitMastery.Tier next = mastery.next(level);
            if (next == null) {
                return Outcome.of(Result.REFUSED, kit, null, "mastery-max");
            }
            if (!next.purchasable()) {
                return Outcome.of(Result.REFUSED, kit, null, "mastery-locked");
            }
            repository.change(() -> {
                if (!viewer.has(KitViewer.BYPASS_COST)) {
                    pay(player, next.upgrade());
                }
                repository.setTier(owner, kit.id(), level + 1);
                return null;
            });
            Messages.send(player, "kits.mastery-bought", kitResolver(kit), Mini.value("tier", next.name()));
            tierUp(player, kit, next);
            KitJournal.self(Tr.t("Kit récupéré"), player, next.upgrade().money(), plain(kit)
                    + Tr.t(" · amélioration au palier ") + next.name());
            return Outcome.of(Result.SUCCESS, kit, null, null);
        } catch (Refusal refusal) {
            return Outcome.of(Result.REFUSED, kit, null, refusal.getMessage());
        } catch (RuntimeException failure) {
            PluginLog.warn(LogTopic.KITS, Tr.t("Amélioration du kit ") + kit.id() + Tr.t(" impossible"), failure);
            return Outcome.of(Result.FAILED, kit, null, "failed");
        } finally {
            claiming.remove(id);
        }
    }

    public boolean toggleFavorite(Player player, Kit kit) {
        return repository.toggleFavorite(player.getUniqueId(), kit.id());
    }

    public Outcome gift(Player giver, Player receiver, Kit kit) {
        UUID id = giver.getUniqueId();
        if (!kit.options().giftable()) {
            return Outcome.of(Result.REFUSED, kit, null, "not-giftable");
        }
        if (giver.getUniqueId().equals(receiver.getUniqueId())) {
            return Outcome.of(Result.REFUSED, kit, null, "self");
        }
        if (!claiming.add(id)) {
            return Outcome.of(Result.BUSY, kit, null, "busy");
        }
        try {
            long now = System.currentTimeMillis();
            KitViewer viewer = viewer(giver);
            KitStatus status = status(viewer, kit, now);
            if (!status.available()) {
                return Outcome.of(Result.REFUSED, kit, status, null);
            }
            KitCost cost = cost(kit, now);
            boolean charge = !viewer.has(KitViewer.BYPASS_COST) && !cost.free();
            UUID owner = viewer.owner(kit);
            repository.change(() -> {
                if (charge) {
                    pay(giver, cost);
                }
                if (kit.limitedStock()) {
                    if (repository.stockUsed(kit.id()) >= kit.stock()) {
                        throw new Refusal("sold-out");
                    }
                    repository.consumeStock(kit.id());
                }
                repository.record(owner, kit.id(), now);
                return null;
            });
            lastClaim.put(id, now);
            List<ItemStack> vouchers = KitItems.vouchers(kit, settings(), 1);
            ItemReturn.give(receiver, vouchers, Tr.t("Kit offert"));
            KitJournal.between(Tr.t("Kit offert"), giver, receiver.getUniqueId(), receiver.getName(),
                    cost.money(), plain(kit) + costDetail(cost, charge));
            planReminder(owner, id, kit);
            return new Outcome(Result.SUCCESS, kit, status, 0, vouchers, null, List.of());
        } catch (Refusal refusal) {
            return Outcome.of(Result.REFUSED, kit, status(giver, kit), refusal.getMessage());
        } catch (RuntimeException failure) {
            PluginLog.warn(LogTopic.KITS, Tr.t("Cadeau du kit ") + kit.id() + Tr.t(" impossible pour ") + giver.getName(), failure);
            return Outcome.of(Result.FAILED, kit, null, "failed");
        } finally {
            claiming.remove(id);
        }
    }

    public void giveVouchers(Player target, Kit kit, int amount, String actor) {
        List<ItemStack> vouchers = KitItems.vouchers(kit, settings(), amount);
        ItemReturn.give(target, vouchers, Tr.t("Bons de kit"));
        KitJournal.byConsole(Tr.t("Bons de kit remis"), target.getUniqueId(), target.getName(), 0.0D,
                amount + Tr.t(" bon(s) ") + plain(kit) + Tr.t(" par ") + actor);
    }

    private void pay(Player player, KitCost cost) {
        UUID id = player.getUniqueId();
        if (cost.shards() > 0L) {
            throw new Refusal("shards");
        }
        if (cost.levels() > 0 && player.getLevel() < cost.levels()) {
            throw new Refusal("levels");
        }
        if (cost.money() > 0.0D && !economy.withdraw(id, cost.money())) {
            throw new Refusal("money");
        }
        if (cost.levels() > 0) {
            int level = player.getLevel();
            float progress = player.getExp();
            repository.watch(() -> {
                player.setLevel(level);
                player.setExp(progress);
            });
            player.setLevel(level - cost.levels());
        }
    }

    private void reward(Player player, KitRewards rewards, int percent) {
        UUID id = player.getUniqueId();
        double money = Numbers.round(rewards.money() * percent / 100.0D);
        if (money > 0.0D && !economy.deposit(id, money)) {
            throw new Refusal("balance-full");
        }
        int levels = (int) Math.min(Integer.MAX_VALUE, (long) rewards.levels() * percent / 100L);
        if (levels > 0) {
            int level = player.getLevel();
            float progress = player.getExp();
            repository.watch(() -> {
                player.setLevel(level);
                player.setExp(progress);
            });
            player.setLevel((int) Math.min(Integer.MAX_VALUE, (long) level + levels));
        }
        for (Map.Entry<String, Integer> key : rewards.keys().entrySet()) {
            if (crates.crate(key.getKey()).isPresent()) {
                crates.give(id, key.getKey(), key.getValue());
            }
        }
    }

    record Delivery(List<ItemStack> items, int stashed) {
    }

    private Delivery deliver(Player player, Kit kit, List<KitPool.Pull> pulls, List<ItemStack> extras,
                             KitSettings settings) {
        long now = System.currentTimeMillis();
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> given = new ArrayList<>();
        List<ItemStack> pending = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : kit.contents().entrySet()) {
            int slot = entry.getKey();
            ItemStack item = prepare(entry.getValue(), kit, settings, now);
            given.add(item.clone());
            boolean equipment = KitSlots.equipment(slot);
            if ((!equipment || kit.options().autoEquip()) && empty(inventory.getItem(slot))) {
                inventory.setItem(slot, item);
            } else {
                pending.add(item);
            }
        }
        for (ItemStack extra : extras) {
            ItemStack item = prepare(extra, kit, settings, now);
            given.add(item.clone());
            pending.add(item);
        }
        for (KitPool.Pull pull : pulls) {
            if (pull.entry().item() == null) {
                continue;
            }
            ItemStack template = prepare(pull.entry().item(), kit, settings, now);
            List<ItemStack> stacks = Inventories.split(template, pull.amount());
            for (ItemStack stack : stacks) {
                given.add(stack.clone());
            }
            pending.addAll(stacks);
        }
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack item : pending) {
            leftovers.addAll(inventory.addItem(item).values());
        }
        int stashed = 0;
        if (!leftovers.isEmpty()) {
            for (ItemStack leftover : leftovers) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                stashed += leftover.getAmount();
            }
        }
        return new Delivery(given, stashed);
    }

    private static ItemStack prepare(ItemStack item, Kit kit, KitSettings settings, long now) {
        ItemStack prepared = kit.options().protectItems() ? KitItems.protect(item, kit, settings.protectedLore())
                : item.clone();
        if (kit.lifetime() > 0L) {
            prepared = KitItems.expiring(prepared, now + kit.lifetime(), settings.zone());
        }
        return prepared;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private void after(Player player, Kit kit, Source source, Delivery delivery, boolean charged, KitCost cost,
                       int bonus, int streak, int levelBefore, int levelAfter, KitMastery mastery, boolean roulette) {
        String name = player.getName();
        effects(player, kit.rewards(), kit);
        if (source.feedback) {
            Messages.send(player, "kits.claimed", kitResolver(kit), Mini.value("items",
                    String.valueOf(delivery.items().size())));
            if (delivery.stashed() > 0) {
                Messages.send(player, "kits.claimed-stash", Mini.value("amount", String.valueOf(delivery.stashed())));
            }
        } else if (source == Source.FIRST_JOIN) {
            Messages.send(player, "kits.first-join", kitResolver(kit));
        } else if (source == Source.RESPAWN) {
            Messages.send(player, "kits.respawn", kitResolver(kit));
        }
        if (bonus > 0 && (kit.rewards().money() > 0.0D || kit.rewards().shards() > 0L || kit.rewards().levels() > 0)
                && source != Source.BULK) {
            Messages.send(player, "kits.bonus", Mini.value("bonus", String.valueOf(bonus)));
        }
        if (streak > 1) {
            Messages.send(player, "kits.streak", kitResolver(kit), Mini.value("streak", String.valueOf(streak)));
        }
        if (levelAfter > levelBefore) {
            KitMastery.Tier tier = mastery.tier(levelAfter);
            if (tier != null) {
                tierUp(player, kit, tier);
            }
        }
        if (kit.options().announce() && settings().broadcasts() && source != Source.ADMIN && source != Source.BULK) {
            Messages.broadcast("kits.broadcast", Mini.value("player", name), kitResolver(kit));
        }
        PlayerSettings preferences = playerSettings.get(player.getUniqueId());
        if (preferences.sounds() && source != Source.BULK) {
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.6F);
            player.playSound(player, Sound.BLOCK_ENDER_CHEST_OPEN, 0.5F, 1.2F);
        }
        if (!roulette && kit.options().animation() && settings().animation() && preferences.particles()
                && source != Source.BULK) {
            try {
                animator.accept(new Claimed(player, kit, List.copyOf(delivery.items().subList(0,
                        Math.min(PREVIEW_LIMIT, delivery.items().size()))), levelAfter));
            } catch (RuntimeException failure) {
                PluginLog.warn(LogTopic.KITS, Tr.t("Animation du kit ") + kit.id() + Tr.t(" impossible"), failure);
            }
        }
        String detail = plain(kit) + (source == Source.MENU || source == Source.COMMAND ? ""
                : " · " + sourceLabel(source)) + costDetail(cost, charged) + " · " + delivery.items().size()
                + Tr.t(" objet(s)") + (delivery.stashed() > 0 ? ", " + delivery.stashed() + Tr.t(" en réserve") : "")
                + (streak > 1 ? Tr.t(" · série ") + streak : "") + (bonus > 0 ? " · bonus " + bonus + "%" : "");
        KitJournal.self(Tr.t("Kit récupéré"), player, charged ? cost.money() : 0.0D, detail);
        if (kit.rewards().money() > 0.0D) {
            KitJournal.self(Tr.t("Argent de kit"), player, Numbers.round(kit.rewards().money() * (100 + bonus)
                    / 100.0D), plain(kit));
        }
        planReminder(ownerOf(player, kit), player.getUniqueId(), kit);
    }

    private UUID ownerOf(Player player, Kit kit) {
        if (!kit.options().team()) {
            return player.getUniqueId();
        }
        UUID team = teams.apply(player.getUniqueId());
        return team == null ? player.getUniqueId() : team;
    }

    private void effects(Player player, KitRewards rewards, Kit kit) {
        String name = player.getName();
        String uuid = player.getUniqueId().toString();
        for (KitDispatch command : rewards.commands()) {
            String resolved = command.resolve(name, uuid, kit.id());
            if (command.console()) {
                Scheduling.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved));
            } else {
                player.performCommand(resolved);
            }
        }
        for (KitEffect effect : rewards.effects()) {
            PotionEffectType type = effectType(effect.type());
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, effect.seconds() * 20, effect.amplifier()));
            }
        }
        Component kitName = Mini.label(kit.name());
        for (String message : rewards.messages()) {
            player.sendMessage(Mini.parse(message, Mini.value("player", name), Mini.component("kit", kitName)));
        }
        for (Map.Entry<String, Integer> key : rewards.keys().entrySet()) {
            crates.crate(key.getKey()).map(CrateBridge.KeyCrate::displayName).ifPresent(crate -> Messages.send(player,
                    "crates.keys-received", Mini.value("amount", String.valueOf(key.getValue())),
                    Mini.styled("crate", crate)));
        }
    }

    private void celebrate(Player player, KitRewards rewards, String label, Kit kit) {
        effects(player, rewards, kit);
        Messages.send(player, "kits.milestone", Mini.value("milestone", label));
        if (playerSettings.get(player.getUniqueId()).sounds()) {
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.2F);
        }
        if (rewards.money() > 0.0D) {
            KitJournal.self(Tr.t("Argent de kit"), player, rewards.money(), Tr.t("palier ") + label);
        }
    }

    private void tierUp(Player player, Kit kit, KitMastery.Tier tier) {
        Messages.send(player, "kits.mastery-up", kitResolver(kit), Mini.value("tier", tier.name()));
        PlayerSettings preferences = playerSettings.get(player.getUniqueId());
        if (preferences.sounds()) {
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
        }
        if (preferences.particles()) {
            Particles.show(player, Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D), 60, 0.5D,
                    0.8D, 0.5D, 0.3D);
        }
    }

    private static String costDetail(KitCost cost, boolean charged) {
        if (!charged || cost.free()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (cost.money() > 0.0D) {
            parts.add(Numbers.money(cost.money()));
        }
        if (cost.shards() > 0L) {
            parts.add(Numbers.count(cost.shards()) + Tr.t(" fragments"));
        }
        if (cost.levels() > 0) {
            parts.add(cost.levels() + Tr.t(" niveaux"));
        }
        return Tr.t(" · payé ") + String.join(", ", parts);
    }

    static String sourceLabel(Source source) {
        return switch (source) {
            case MENU, COMMAND -> Tr.t("réclamé");
            case VOUCHER -> Tr.t("bon utilisé");
            case GIFT -> "offert";
            case FIRST_JOIN -> Tr.t("première connexion");
            case RESPAWN -> Tr.t("réapparition");
            case BULK -> Tr.t("tout récupérer");
            case ADMIN -> Tr.t("donné par un admin");
        };
    }

    private static PotionEffectType effectType(String type) {
        NamespacedKey key = NamespacedKey.fromString(type.contains(":") ? type : "minecraft:" + type);
        return key == null ? null : Registry.EFFECT.get(key);
    }

    public static TagResolver kitResolver(Kit kit) {
        return Mini.component("kit", Mini.label(kit.name()));
    }

    public static String plain(Kit kit) {
        return Mini.plain(Mini.label(kit.name()));
    }

    public int reset(UUID player, String kit) {
        int removed = repository.reset(player, kit);
        if (kit == null) {
            reminders.remove(player);
        } else {
            Map<String, Long> planned = reminders.get(player);
            if (planned != null) {
                planned.remove(kit);
            }
        }
        return removed;
    }

    public void onJoin(Player player) {
        planReminders(player.getUniqueId());
        Scheduling.entityLater(player, () -> sweep(player), 20L);
        if (!player.hasPlayedBefore()) {
            Scheduling.entityLater(player, () -> firstJoin(player), settings().firstJoinDelayTicks());
            return;
        }
        if (settings().joinSummary()) {
            Scheduling.entityLater(player, () -> summary(player), 60L);
        }
    }

    public void onQuit(UUID player) {
        reminders.remove(player);
        lastClaim.remove(player);
        String prefix = player.toString();
        warned.removeIf(key -> key.startsWith(prefix));
    }

    private void firstJoin(Player player) {
        if (!player.isOnline()) {
            return;
        }
        for (Kit kit : catalog.kits().values()) {
            if (kit.options().firstJoin()) {
                claim(player, kit, Source.FIRST_JOIN);
            }
        }
    }

    public void onRespawn(Player player) {
        long now = System.currentTimeMillis();
        for (Kit kit : catalog.kits().values()) {
            if (kit.options().respawn() && cost(kit, now).free()) {
                KitStatus status = status(player, kit);
                if (status.available()) {
                    claim(player, kit, Source.RESPAWN);
                }
            }
        }
    }

    public int sweep(Player player) {
        if (!player.isOnline()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int removed = KitItems.sweep(player.getInventory(), now);
        ItemStack cursor = player.getItemOnCursor();
        if (KitItems.expired(cursor, now)) {
            removed += cursor.getAmount();
            player.setItemOnCursor(null);
        }
        if (removed > 0) {
            Messages.send(player, "kits.expired", Mini.value("amount", String.valueOf(removed)));
        }
        return removed;
    }

    private void summary(Player player) {
        if (!player.isOnline()) {
            return;
        }
        int available = availableCount(player);
        if (available > 0) {
            Messages.send(player, "kits.join-summary", Mini.value("amount", String.valueOf(available)));
        }
    }

    private void planReminders(UUID player) {
        Map<String, KitClaim> claims = repository.claimsOf(player);
        for (KitClaim claim : claims.values()) {
            catalog.kit(claim.kit()).ifPresent(kit -> planReminder(player, player, kit));
        }
        UUID team = teams.apply(player);
        if (team != null) {
            for (KitClaim claim : repository.claimsOf(team).values()) {
                catalog.kit(claim.kit()).filter(kit -> kit.options().team())
                        .ifPresent(kit -> planReminder(team, player, kit));
            }
        }
    }

    private void planReminder(UUID owner, UUID player, Kit kit) {
        if (!settings().reminders() || !kit.timed() || !kit.repeatable()) {
            return;
        }
        KitClaim claim = repository.find(owner, kit.id());
        if (claim == null || (kit.limitedUses() && claim.uses() >= kit.maxUses())) {
            return;
        }
        long ready = KitRules.readyAt(kit, claim, 0, settings().zone());
        if (ready > System.currentTimeMillis()) {
            reminders.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>()).put(kit.id(), ready);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        remind(now);
        announceOpenings(now);
        if (ticks++ % SLOW_CHECK_EVERY == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Scheduling.entity(player, () -> {
                    sweep(player);
                    warnStreaks(player, System.currentTimeMillis());
                });
            }
        }
    }

    private void remind(long now) {
        for (Map.Entry<UUID, Map<String, Long>> entry : reminders.entrySet()) {
            Map<String, Long> planned = entry.getValue();
            List<String> due = new ArrayList<>();
            planned.forEach((kit, at) -> {
                if (at <= now) {
                    due.add(kit);
                }
            });
            if (due.isEmpty()) {
                continue;
            }
            due.forEach(planned::remove);
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Scheduling.entity(player, () -> announceReady(player, due));
        }
    }

    private void announceOpenings(long now) {
        KitCatalog current = catalog;
        for (Kit kit : current.kits().values()) {
            KitSchedule schedule = kit.requirements().schedule();
            if (schedule.always()) {
                openKits.remove(kit.id());
                continue;
            }
            boolean open = schedule.open(now, current.settings().zone());
            Boolean was = openKits.put(kit.id(), open);
            if (Boolean.FALSE.equals(was) && open && current.settings().broadcasts()) {
                Messages.broadcast("kits.opened", kitResolver(kit), Mini.value("id", kit.id()));
            }
        }
    }

    private void warnStreaks(Player player, long now) {
        if (!player.isOnline() || !playerSettings.get(player.getUniqueId()).enabled(PlayerSettings.Setting.KIT_REMINDERS)) {
            return;
        }
        KitProgression.Streaks streaks = catalog.progression().streaks();
        if (!streaks.enabled() || streaks.warning() <= 0L) {
            return;
        }
        KitViewer viewer = viewer(player);
        for (Kit kit : catalog.kits().values()) {
            if (!streaksOn(kit)) {
                continue;
            }
            KitClaim claim = repository.find(viewer.owner(kit), kit.id());
            if (!KitProgression.streakAtRisk(kit, claim, now, settings().zone(), streaks.warning())) {
                continue;
            }
            long period = kit.reset().periodStart(now, settings().zone(), kit.resetTime(), kit.resetDay());
            if (warned.add(player.getUniqueId() + ":" + kit.id() + ":" + period) && status(viewer, kit, now).available()) {
                long left = kit.reset().nextReset(now, settings().zone(), kit.resetTime(), kit.resetDay()) - now;
                Messages.send(player, "kits.streak-danger", kitResolver(kit), Mini.value("id", kit.id()),
                        Mini.value("streak", String.valueOf(claim.streak())), Mini.value("time", Numbers.duration(left)));
            }
        }
    }

    private void announceReady(Player player, List<String> kits) {
        if (!player.isOnline() || !playerSettings.get(player.getUniqueId()).enabled(PlayerSettings.Setting.KIT_REMINDERS)) {
            return;
        }
        for (String id : kits) {
            Optional<Kit> found = catalog.kit(id);
            if (found.isEmpty()) {
                continue;
            }
            Kit kit = found.get();
            KitStatus status = status(player, kit);
            if (status.state() == KitStatus.State.COOLDOWN) {
                planReminder(ownerOf(player, kit), player.getUniqueId(), kit);
            } else if (status.available()) {
                Messages.send(player, "kits.ready", kitResolver(kit), Mini.value("id", kit.id()));
                if (playerSettings.get(player.getUniqueId()).sounds()) {
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.6F);
                }
            }
        }
    }

    static final class Refusal extends RuntimeException {

        Refusal(String reason) {
            super(reason, null, false, false);
        }
    }
}
