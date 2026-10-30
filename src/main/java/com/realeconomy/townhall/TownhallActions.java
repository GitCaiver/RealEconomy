package com.realeconomy.townhall;

import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.network.OpenTownhallPayload;
import com.realeconomy.network.TownhallActionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Lógica de servidor del ayuntamiento (venta de licencias). */
public final class TownhallActions {

    private TownhallActions() {
    }

    /** Moneda con la que se paga la licencia, según la config. Null si no está configurada. */
    public static Currency licenseCurrency(MinecraftServer server) {
        String tag = ModConfig.get().licenseCurrencyTag;
        if (tag == null || tag.isBlank()) {
            return null;
        }
        Group g = GroupManager.get(server).getByTag(tag);
        if (g == null) {
            return null;
        }
        return CurrencyManager.get(server).getByGroup(g.getId());
    }

    public static void openFor(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        Currency c = licenseCurrency(server);
        int available = LicenseManager.get(server).get(player.getUuid());
        OpenTownhallPayload payload = new OpenTownhallPayload(
                ModConfig.get().licensePrice,
                c == null ? "" : c.getId(),
                c == null ? "" : c.getName(),
                available,
                c != null
        );
        ServerPlayNetworking.send(player, payload);
    }

    public static void handle(ServerPlayerEntity player, TownhallActionPayload action) {
        if (!TownhallEntities.isNear(player)) {
            player.sendMessage(Text.literal("No hay ningún ayuntamiento cerca.").formatted(Formatting.RED));
            return;
        }
        if (action.action() == TownhallActionPayload.BUY_LICENSE) {
            buyLicense(player);
        }
        openFor(player);
    }

    private static void buyLicense(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        Currency c = licenseCurrency(server);
        if (c == null) {
            player.sendMessage(Text.literal("El administrador no ha configurado la moneda de la licencia.")
                    .formatted(Formatting.RED));
            return;
        }
        int price = ModConfig.get().licensePrice;
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(player.getUuid(), c.getId(), price)) {
            player.sendMessage(Text.literal("Saldo insuficiente. La licencia cuesta " + price + " " + c.getName() + ".")
                    .formatted(Formatting.RED));
            return;
        }
        LicenseManager.get(server).add(player.getUuid(), 1);
        player.sendMessage(Text.literal("Licencia de tienda comprada. Úsala para montar una tienda (parte 5).")
                .formatted(Formatting.GREEN));
    }
}
