package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.text.Tr;
import org.bukkit.Material;

import java.time.ZoneId;
import java.util.List;

public record KitSettings(ZoneId zone, boolean showLocked, boolean reminders, boolean joinSummary, boolean animation,
                          boolean broadcasts, String protectedLore, Material voucherMaterial, String voucherName,
                          List<String> voucherLore, long firstJoinDelayTicks, long claimSpacingMillis) {

    public static final String DEFAULT_ZONE = "Europe/Paris";
    public static final String DEFAULT_PROTECTED_LORE = Tr.t("<#6E7681>⛓ Objet de kit, invendable");
    public static final String DEFAULT_VOUCHER_NAME = Tr.t("<#A78BFA><b>Bon de kit</b> <#6E7681>▸</#6E7681> <kit>");
    public static final List<String> DEFAULT_VOUCHER_LORE = List.of(
            "<#6E7681>[ʙᴏɴ ᴅᴇ ᴋɪᴛ]",
            "",
            Tr.t("<#C9D1D9>Ce bon contient le kit <kit><#C9D1D9>."),
            Tr.t("<#C9D1D9>Il se garde, s'échange et s'offre."),
            "",
            "<#FBBF24>ᐅ <b>ᴄʟɪᴄ ᴅʀᴏɪᴛ</b> <#C9D1D9>pour l'ouvrir");

    public static final KitSettings DEFAULTS = new KitSettings(ZoneId.of(DEFAULT_ZONE), true, true, true, true, true,
            DEFAULT_PROTECTED_LORE, Material.PAPER, DEFAULT_VOUCHER_NAME, DEFAULT_VOUCHER_LORE, 60L, 750L);

    public KitSettings {
        zone = zone == null ? ZoneId.of(DEFAULT_ZONE) : zone;
        protectedLore = protectedLore == null ? "" : protectedLore;
        voucherMaterial = voucherMaterial == null ? Material.PAPER : voucherMaterial;
        voucherName = voucherName == null || voucherName.isBlank() ? DEFAULT_VOUCHER_NAME : voucherName;
        voucherLore = voucherLore == null ? List.of() : List.copyOf(voucherLore);
        firstJoinDelayTicks = Math.max(1L, firstJoinDelayTicks);
        claimSpacingMillis = Math.max(0L, claimSpacingMillis);
    }
}
