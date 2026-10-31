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
        String formName = "";
        if (country != null) {
            countryTag = country.getTag();
            countryColor = (country.getColor() == null ? Formatting.WHITE : country.getColor()).getName();
            formName = com.realeconomy.politics.PoliticalForms.displayName(country.getForm(), country.getSubtype());
            taxRate = FinanceManager.get(server).getTaxRate(country.getId());
            Currency cc = CurrencyManager.get(server).getByGroup(country.getId());
            if (cc != null) {
                MonetaryManager mm = MonetaryManager.get(server);
                circulation = mm.circulation(cc.getId());
                value = mm.value(cc.getId());
                canPrint = com.realeconomy.politics.Governance.isGovernment(server, country, player.getUuid());
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
                taxRate,
                formName
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
            openFor(player);
        } else if (action.action() == TownhallActionPayload.PRINT) {
            printMoney(player, action.amount());
            openFor(player);
        } else if (action.action() == TownhallActionPayload.SET_TAX) {
            setTax(player, (int) action.amount());
            openFor(player);
        } else if (action.action() == TownhallActionPayload.OPEN_TAXES) {
            openTaxPanel(player);
        } else if (action.action() == TownhallActionPayload.OPEN_LICENSES) {
            openLicensePanel(player);
        }
    }

    // ---- Panel de licencias personalizadas (parte 5, 3ª tanda) ----

    /** Envía el panel de licencias personalizadas del país del jugador. */
    public static void openLicensePanel(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        Group country = GroupManager.get(server).getByPlayer(player.getUuid());
        if (country == null) {
            player.sendMessage(Text.literal("No perteneces a ningún país.").formatted(Formatting.RED));
            return;
        }
        com.realeconomy.license.CustomLicenseManager clm = com.realeconomy.license.CustomLicenseManager.get(server);
        CurrencyManager cm = CurrencyManager.get(server);
        java.util.List<com.realeconomy.network.LicenseView> views = new java.util.ArrayList<>();
        for (com.realeconomy.license.License l : clm.defs(country.getId())) {
            Currency cur = cm.getById(l.getCurrencyId());
            String curName = cur == null ? "¿?" : cur.getName();
            String curColor = "gold";
            Group issuer = GroupManager.get(server).getById(cur == null ? null : cur.getGroupId());
            if (issuer != null && issuer.getColor() != null) {
                curColor = issuer.getColor().getName();
            }
            views.add(new com.realeconomy.network.LicenseView(
                    l.getId(), l.getName(), l.getPrice(), curName, curColor,
                    l.getDurationMillis(), l.getPermission().description(),
                    clm.heldExpiry(player.getUuid(), l.getId())));
        }
        ServerPlayNetworking.send(player, new com.realeconomy.network.OpenLicensesPayload(
                com.realeconomy.politics.Governance.isGovernment(server, country, player.getUuid()),
                country.getTag(),
                (country.getColor() == null ? Formatting.WHITE : country.getColor()).getName(),
                views));
    }

    public static void handleLicenseAction(ServerPlayerEntity player, com.realeconomy.network.LicenseActionPayload action) {
        if (!TownhallEntities.isNear(player)) {
            player.sendMessage(Text.literal("No hay ningún ayuntamiento cerca.").formatted(Formatting.RED));
            return;
        }
        MinecraftServer server = player.getServer();
        Group country = GroupManager.get(server).getByPlayer(player.getUuid());
        if (country == null) {
            return;
        }
        com.realeconomy.license.CustomLicenseManager clm = com.realeconomy.license.CustomLicenseManager.get(server);
        com.realeconomy.license.License def = clm.defById(action.defId());
        if (def == null || !def.getCountryId().equals(country.getId())) {
            player.sendMessage(Text.literal("Esa licencia ya no existe.").formatted(Formatting.RED));
            openLicensePanel(player);
            return;
        }
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(player.getUuid(), def.getCurrencyId(), def.getPrice())) {
            Currency cur = CurrencyManager.get(server).getById(def.getCurrencyId());
            player.sendMessage(Text.literal("Saldo insuficiente. Cuesta " + def.getPrice() + " "
                    + (cur == null ? "" : cur.getName()) + ".").formatted(Formatting.RED));
            openLicensePanel(player);
            return;
        }
        bm.add(FinanceManager.accountUuid(country), def.getCurrencyId(), def.getPrice());
        clm.grant(player.getUuid(), def, System.currentTimeMillis());
        player.sendMessage(Text.literal("Licencia \"" + def.getName() + "\" comprada. Permite "
                + def.getPermission().description() + ".").formatted(Formatting.GREEN));
        openLicensePanel(player);
    }

    // ---- Panel de impuestos por tipo (parte 4, 3ª tanda) ----

    /** Envía el panel de impuestos por tipo del país del jugador. */
    public static void openTaxPanel(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        Group country = GroupManager.get(server).getByPlayer(player.getUuid());
        if (country == null) {
            player.sendMessage(Text.literal("No perteneces a ningún país.").formatted(Formatting.RED));
            return;
        }
        FinanceManager fm = FinanceManager.get(server);
        var p = fm.getPolicy(country.getId());
        boolean gov = com.realeconomy.politics.Governance.isGovernment(server, country, player.getUuid());
        ServerPlayNetworking.send(player, new com.realeconomy.network.OpenTaxPolicyPayload(
                gov,
                country.getTag(),
                (country.getColor() == null ? Formatting.WHITE : country.getColor()).getName(),
                fm.getTaxRate(country.getId()),
                p.shopSalePercent,
                p.marketSalePercent,
                p.transferPercent,
                fm.effectiveExchangePercent(country.getId()), p.exchangePercent < 0,
                fm.effectiveAuctionPercent(country.getId()), p.auctionPercent < 0,
                p.shopFeeAmount, p.shopFeePeriod));
    }

    public static void handleTaxAction(ServerPlayerEntity player, com.realeconomy.network.TaxPolicyActionPayload action) {
        if (!TownhallEntities.isNear(player)) {
            player.sendMessage(Text.literal("No hay ningún ayuntamiento cerca.").formatted(Formatting.RED));
            return;
        }
        MinecraftServer server = player.getServer();
        Group country = GroupManager.get(server).getByPlayer(player.getUuid());
        if (country == null) {
            return;
        }
        if (!com.realeconomy.politics.Governance.isGovernment(server, country, player.getUuid())) {
            player.sendMessage(Text.literal(
                    com.realeconomy.politics.Governance.notGovernmentMessage(server, country)).formatted(Formatting.RED));
            return;
        }
        FinanceManager fm = FinanceManager.get(server);
        var p = fm.editPolicy(country.getId());
        int d = action.delta();
        switch (action.type()) {
            case "renta" -> fm.setTaxRate(country.getId(), Math.min(
                    com.realeconomy.politics.LawEnforcement.maxTax(country), clamp(fm.getTaxRate(country.getId()) + d)));
            case "ventatienda" -> p.shopSalePercent = clamp(p.shopSalePercent + d);
            case "mercado" -> p.marketSalePercent = clamp(p.marketSalePercent + d);
            case "transferencia" -> p.transferPercent = clamp(p.transferPercent + d);
            case "cambio" -> p.exchangePercent = p.exchangePercent < 0
                    ? clamp(fm.effectiveExchangePercent(country.getId()) + d) : clamp(p.exchangePercent + d);
            case "subasta" -> p.auctionPercent = p.auctionPercent < 0
                    ? clamp(fm.effectiveAuctionPercent(country.getId()) + d) : clamp(p.auctionPercent + d);
            case "cuotatienda" -> {
                p.shopFeeAmount = Math.max(0, p.shopFeeAmount + d);
                p.shopFeeLast = System.currentTimeMillis();
            }
            default -> {
            }
        }
        fm.savePolicy();
        openTaxPanel(player);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static void setTax(ServerPlayerEntity player, int rate) {
        MinecraftServer server = player.getServer();
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null || !com.realeconomy.politics.Governance.isGovernment(server, g, player.getUuid())) {
            player.sendMessage(Text.literal(g == null ? "No perteneces a ningún país."
                    : com.realeconomy.politics.Governance.notGovernmentMessage(server, g)).formatted(Formatting.RED));
            return;
        }
        int cap = com.realeconomy.politics.LawEnforcement.maxTax(g);
        int applied = Math.max(0, Math.min(cap, rate));
        FinanceManager.get(server).setTaxRate(g.getId(), applied);
        if (rate > cap) {
            player.sendMessage(Text.literal("La ley limita el impuesto al " + cap + "%; ajustado a ese máximo.")
                    .formatted(Formatting.YELLOW), false);
        } else {
            player.sendMessage(Text.literal("Impuesto general del país ajustado al " + applied + "%.")
                    .formatted(Formatting.GREEN), false);
        }
    }

    private static void printMoney(ServerPlayerEntity player, long amount) {
        MinecraftServer server = player.getServer();
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null || !com.realeconomy.politics.Governance.isGovernment(server, g, player.getUuid())) {
            player.sendMessage(Text.literal(g == null ? "No perteneces a ningún país."
                    : com.realeconomy.politics.Governance.notGovernmentMessage(server, g)).formatted(Formatting.RED));
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
        String lawError = com.realeconomy.politics.LawEnforcement.whyCannotPrint(server, g, amount);
        if (lawError != null) {
            player.sendMessage(Text.literal(lawError).formatted(Formatting.RED));
            return;
        }
        if (com.realeconomy.politics.LawEnforcement.isBigPrint(g, amount)) {
            com.realeconomy.politics.BigDecisionManager.get(server).propose(server, g, "print", amount, player);
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
