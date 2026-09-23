package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.api.LoadoutApi;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

final class LoadoutService implements LoadoutApi {

    private final KitService service;
    private final KitMenu menu;
    private final KitPreviewMenu preview;

    LoadoutService(KitService service, KitMenu menu, KitPreviewMenu preview) {
        this.service = service;
        this.menu = menu;
        this.preview = preview;
    }

    @Override
    public List<String> kits() {
        return List.copyOf(service.catalog().kits().keySet());
    }

    @Override
    public boolean exists(String kit) {
        return service.kit(kit).isPresent();
    }

    @Override
    public boolean available(Player player, String kit) {
        return service.status(player, require(kit)).available();
    }

    @Override
    public long cooldownRemaining(Player player, String kit) {
        KitStatus status = service.status(player, require(kit));
        return status.state() == KitStatus.State.COOLDOWN ? Math.max(0L, status.remaining()) : 0L;
    }

    @Override
    public int uses(UUID player, String kit) {
        KitClaim claim = service.repository().find(player, require(kit).id());
        return claim == null ? 0 : claim.uses();
    }

    @Override
    public boolean claim(Player player, String kit) {
        return service.claim(player, require(kit), KitService.Source.COMMAND).success();
    }

    @Override
    public boolean give(Player player, String kit) {
        return service.claim(player, require(kit), KitService.Source.ADMIN).success();
    }

    @Override
    public void giveVouchers(Player player, String kit, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        service.giveVouchers(player, require(kit), amount, "API");
    }

    @Override
    public int reset(UUID player, String kit) {
        return service.reset(player, require(kit).id());
    }

    @Override
    public int resetAll(UUID player) {
        return service.reset(player, null);
    }

    @Override
    public void openMenu(Player player) {
        menu.open(player);
    }

    @Override
    public void preview(Player player, String kit) {
        preview.open(player, require(kit), null);
    }

    private Kit require(String id) {
        return service.kit(id).orElseThrow(() -> new IllegalArgumentException("unknown kit: " + id));
    }
}
