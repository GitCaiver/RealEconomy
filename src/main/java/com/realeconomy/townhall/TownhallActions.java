package com.realeconomy.townhall;

import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.economy.MonetaryManager;
import com.realeconomy.finance.FinanceManager;
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

        // Política monetaria del país del jugador (parte 3).
        Group country = GroupManager.get(server).getByPlayer(player.getUuid());
        boolean hasCountry = country != null;
        String countryTag = "";
        String countryColor = "white";
        long circulation = 0L;
        double value = 0.0;
        boolean canPrint = false;
        int taxRate = 0;
        if (country != null) {
            countryTag = country.getTag();
            countryColor = (country.getColor() == null ? Formatting.WHITE : country.getColor()).getName();
            taxRate = FinanceManager.get(server).getTaxRate(country.getId());
            Currency cc = CurrencyManager.get(server).getByGroup(country.getId());
            if (cc != null) {
                MonetaryManager mm = MonetaryManager.get(server);
                circulation = mm.circulation(cc.getId());
                value = mm.value(cc.getId());
                canPrint = country.isLeader(player.getUuid());
            }
        }

        OpenTownhallPayload payload = new OpenTownhallPayload(
                ModConfig.get().licensePrice,
                c == null ? "" : c.getId(),
                c == null ? "" : c.getName(),
                available,
                c != null,
                hasCountry,
                countryTag,
                countryColor,
                circulation,
                value,
                canPrint,
                taxRate
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
        } else if (action.action() == TownhallActionPayload.PRINT) {
            printMoney(player, action.amount());
        } else if (action.action() == TownhallActionPayload.SET_TAX) {
            setTax(player, (int) action.amount());
        }
        openFor(player);
    }

    private static void setTax(ServerPlayerEntity player, int rate) {
        MinecraftServer server = player.getServer();
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            player.sendMessage(Text.literal("Solo el líder del país puede ajustar los impuestos.").formatted(Formatting.RED));
            return;
        }
        FinanceManager.get(server).setTaxRate(g.getId(), rate);
        player.sendMessage(Text.literal("Impuesto general del país ajustado al " + Math.max(0, Math.min(100, rate)) + "%.")
                .formatted(Formatting.GREEN), false);
    }

    private static void printMoney(ServerPlayerEntity player, long amount) {
        MinecraftServer server = player.getServer();
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            player.sendMessage(Text.literal("Solo el líder del país puede imprimir dinero.").formatted(Formatting.RED));
            return;
        }
        Currency cc = CurrencyManager.get(server).getByGroup(g.getId());
        if (cc == null) {
            player.sendMessage(Text.literal("Tu país no tiene moneda.").formatted(Formatting.RED));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(Text.literal("Indica una cantidad válida para imprimir.").formatted(Formatting.RED));
            return;
        }
        BalanceManager.get(server).add(FinanceManager.accountUuid(g), cc.getId(), amount);
        MonetaryManager mm = MonetaryManager.get(server);
        mm.print(cc.getId(), amount, player.getNameForScoreboard());
        player.sendMessage(Text.literal("Impresas " + amount + " " + cc.getName()
                + " para la cuenta del país. Circulación: " + mm.circulation(cc.getId())
                + " · valor: " + String.format(java.util.Locale.ROOT, "%.3f", mm.value(cc.getId()))
                + " esmeraldas/moneda.").formatted(Formatting.GREEN), false);
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
