package com.realeconomy.network;

import com.realeconomy.bank.BankActions;
import com.realeconomy.buyer.BuyerActions;
import com.realeconomy.market.MarketActions;
import com.realeconomy.shop.ShopActions;
import com.realeconomy.townhall.TownhallActions;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.IconCache;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registro de canales S2C y helpers de envío a clientes. Lado común (el registro
 * de tipos de payload debe ocurrir en ambos lados).
 */
public final class ModNetworking {

    private ModNetworking() {
    }

    /** Registra los tipos de payload. Llamar en la init común. */
    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(CurrencyListPayload.ID, CurrencyListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CurrencyIconPayload.ID, CurrencyIconPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenBankPayload.ID, OpenBankPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BankActionPayload.ID, BankActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenTownhallPayload.ID, OpenTownhallPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TownhallActionPayload.ID, TownhallActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenShopPayload.ID, OpenShopPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShopActionPayload.ID, ShopActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenMarketPayload.ID, OpenMarketPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MarketActionPayload.ID, MarketActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenBuyerPayload.ID, OpenBuyerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BuyerActionPayload.ID, BuyerActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenTaxPolicyPayload.ID, OpenTaxPolicyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TaxPolicyActionPayload.ID, TaxPolicyActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenLicensesPayload.ID, OpenLicensesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LicenseActionPayload.ID, LicenseActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenCountryCreatePayload.ID, OpenCountryCreatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CountryCreateActionPayload.ID, CountryCreateActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenElectionPayload.ID, OpenElectionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ElectionVotePayload.ID, ElectionVotePayload.CODEC);
    }

    /** Registra los receptores de paquetes del cliente (C2S). Llamar en la init común. */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(BankActionPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> BankActions.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TownhallActionPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> TownhallActions.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ShopActionPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> ShopActions.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(MarketActionPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> MarketActions.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(BuyerActionPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> BuyerActions.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TaxPolicyActionPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> TownhallActions.handleTaxAction(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(LicenseActionPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> TownhallActions.handleLicenseAction(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(CountryCreateActionPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> com.realeconomy.politics.CreationActions.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ElectionVotePayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> com.realeconomy.election.ElectionActions.handleVote(context.player(), payload)));
    }

    /** Envía a un jugador toda la info de monedas (lista + iconos). */
    public static void sendAllTo(MinecraftServer server, ServerPlayerEntity player) {
        CurrencyManager cm = CurrencyManager.get(server);
        ServerPlayNetworking.send(player, new CurrencyListPayload(cm.buildEntries()));
        for (Currency c : cm.all()) {
            byte[] png = IconCache.readOrDefault(server, c.getId());
            if (png.length > 0) {
                ServerPlayNetworking.send(player, new CurrencyIconPayload(c.getId(), png));
            }
        }
    }

    /** Difunde la lista de monedas a todos los jugadores conectados. */
    public static void broadcastList(MinecraftServer server) {
        CurrencyListPayload payload = new CurrencyListPayload(CurrencyManager.get(server).buildEntries());
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** Difunde el icono de una moneda a todos los jugadores conectados. */
    public static void broadcastIcon(MinecraftServer server, String currencyId, byte[] png) {
        if (png == null || png.length == 0) {
            return;
        }
        CurrencyIconPayload payload = new CurrencyIconPayload(currencyId, png);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
