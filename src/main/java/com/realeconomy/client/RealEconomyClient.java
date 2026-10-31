package com.realeconomy.client;

import com.realeconomy.item.CoinItem;
import com.realeconomy.item.CoinTooltipData;
import com.realeconomy.item.ModItems;
import com.realeconomy.network.CurrencyEntry;
import com.realeconomy.network.CurrencyIconPayload;
import com.realeconomy.network.CurrencyListPayload;
import com.realeconomy.network.OpenBankPayload;
import com.realeconomy.network.OpenBuyerPayload;
import com.realeconomy.network.OpenMarketPayload;
import com.realeconomy.network.OpenShopPayload;
import com.realeconomy.network.OpenTownhallPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

public class RealEconomyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // El nombre del item de moneda es el de la divisa + siglas del país, con su color.
        CoinItem.NAME_RESOLVER = id -> {
            CurrencyEntry e = ClientCurrencyStore.getEntry(id);
            if (e == null) {
                return null;
            }
            Formatting c = Formatting.byName(e.colorName());
            if (c == null) {
                c = Formatting.GOLD;
            }
            String suffix = e.tag().isEmpty() ? "" : " [" + e.tag() + "]";
            return Text.literal(e.name() + suffix).formatted(c);
        };

        // Recepción de metadatos e iconos de moneda.
        ClientPlayNetworking.registerGlobalReceiver(CurrencyListPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCurrencyStore.updateList(payload.entries())));

        ClientPlayNetworking.registerGlobalReceiver(CurrencyIconPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCurrencyStore.updateIcon(payload.id(), payload.png())));

        // Abre o actualiza la interfaz del banco.
        ClientPlayNetworking.registerGlobalReceiver(OpenBankPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateBank(context.client(), payload)));

        // Abre o actualiza la interfaz del ayuntamiento.
        ClientPlayNetworking.registerGlobalReceiver(OpenTownhallPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateTownhall(context.client(), payload)));

        // Abre o actualiza la interfaz de compra de una tienda.
        ClientPlayNetworking.registerGlobalReceiver(OpenShopPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateShop(context.client(), payload)));

        // Abre o actualiza la interfaz de un mercado de grupo.
        ClientPlayNetworking.registerGlobalReceiver(OpenMarketPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateMarket(context.client(), payload)));

        // Abre o actualiza la interfaz de un comprador por categorías.
        ClientPlayNetworking.registerGlobalReceiver(OpenBuyerPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateBuyer(context.client(), payload)));

        // Abre o actualiza el panel de impuestos por tipo.
        ClientPlayNetworking.registerGlobalReceiver(com.realeconomy.network.OpenTaxPolicyPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().currentScreen instanceof TaxScreen tax) {
                        tax.refresh(payload);
                    } else {
                        context.client().setScreen(new TaxScreen(payload));
                    }
                }));

        // Abre o actualiza el panel de licencias personalizadas.
        ClientPlayNetworking.registerGlobalReceiver(com.realeconomy.network.OpenLicensesPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().currentScreen instanceof LicenseScreen lic) {
                        lic.refresh(payload);
                    } else {
                        context.client().setScreen(new LicenseScreen(payload));
                    }
                }));

        // El dato de tooltip de la moneda se convierte en el componente que pinta el icono.
        TooltipComponentCallback.EVENT.register(data -> {
            if (data instanceof CoinTooltipData coin) {
                return new CoinTooltipComponent(coin.currencyId());
            }
            return null;
        });

        // El nombre de la moneda ya va en el título del item (ver CoinItem). En el tooltip
        // solo avisamos si el cliente aún no conoce esa divisa.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            String currencyId = ModItems.currencyOf(stack);
            if (currencyId == null) {
                return;
            }
            if (ClientCurrencyStore.getEntry(currencyId) == null) {
                lines.add(Text.literal("Moneda desconocida").formatted(Formatting.GRAY));
            }
        });
    }

    private static void openOrUpdateBank(MinecraftClient client, OpenBankPayload payload) {
        if (client.currentScreen instanceof BankScreen bank) {
            bank.refresh(payload);
        } else {
            client.setScreen(new BankScreen(payload));
        }
    }

    private static void openOrUpdateTownhall(MinecraftClient client, OpenTownhallPayload data) {
        if (client.currentScreen instanceof TownhallScreen townhall) {
            townhall.refresh(data);
        } else {
            client.setScreen(new TownhallScreen(data));
        }
    }

    private static void openOrUpdateShop(MinecraftClient client, OpenShopPayload data) {
        if (client.currentScreen instanceof ShopScreen shop) {
            shop.refresh(data);
        } else {
            client.setScreen(new ShopScreen(data));
        }
    }

    private static void openOrUpdateMarket(MinecraftClient client, OpenMarketPayload data) {
        if (client.currentScreen instanceof MarketScreen market) {
            market.refresh(data);
        } else {
            client.setScreen(new MarketScreen(data));
        }
    }

    private static void openOrUpdateBuyer(MinecraftClient client, OpenBuyerPayload data) {
        if (client.currentScreen instanceof BuyerScreen buyer) {
            buyer.refresh(data);
        } else {
            client.setScreen(new BuyerScreen(data));
        }
    }
}
