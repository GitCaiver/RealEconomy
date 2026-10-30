package com.realeconomy.client;

import com.realeconomy.item.CoinTooltipData;
import com.realeconomy.item.ModItems;
import com.realeconomy.network.CurrencyEntry;
import com.realeconomy.network.CurrencyIconPayload;
import com.realeconomy.network.CurrencyListPayload;
import com.realeconomy.network.OpenBankPayload;
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
        // Recepción de metadatos e iconos de moneda.
        ClientPlayNetworking.registerGlobalReceiver(CurrencyListPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCurrencyStore.updateList(payload.entries())));

        ClientPlayNetworking.registerGlobalReceiver(CurrencyIconPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientCurrencyStore.updateIcon(payload.id(), payload.png())));

        // Abre o actualiza la interfaz del banco.
        ClientPlayNetworking.registerGlobalReceiver(OpenBankPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateBank(context.client(), payload.balances())));

        // Abre o actualiza la interfaz del ayuntamiento.
        ClientPlayNetworking.registerGlobalReceiver(OpenTownhallPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateTownhall(context.client(), payload)));

        // Abre o actualiza la interfaz de compra de una tienda.
        ClientPlayNetworking.registerGlobalReceiver(OpenShopPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateShop(context.client(), payload)));

        // Abre o actualiza la interfaz de un mercado de grupo.
        ClientPlayNetworking.registerGlobalReceiver(OpenMarketPayload.ID, (payload, context) ->
                context.client().execute(() -> openOrUpdateMarket(context.client(), payload)));

        // El dato de tooltip de la moneda se convierte en el componente que pinta el icono.
        TooltipComponentCallback.EVENT.register(data -> {
            if (data instanceof CoinTooltipData coin) {
                return new CoinTooltipComponent(coin.currencyId());
            }
            return null;
        });

        // Línea de texto del tooltip: nombre de la moneda con las siglas y color del grupo.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            String currencyId = ModItems.currencyOf(stack);
            if (currencyId == null) {
                return;
            }
            CurrencyEntry entry = ClientCurrencyStore.getEntry(currencyId);
            if (entry == null) {
                lines.add(Text.literal("Moneda desconocida").formatted(Formatting.GRAY));
                return;
            }
            Formatting color = Formatting.byName(entry.colorName());
            if (color == null) {
                color = Formatting.GOLD;
            }
            String prefix = entry.tag().isEmpty() ? "" : "[" + entry.tag() + "] ";
            lines.add(Text.literal(prefix + entry.name()).formatted(color));
        });
    }

    private static void openOrUpdateBank(MinecraftClient client, Map<String, Long> balances) {
        if (client.currentScreen instanceof BankScreen bank) {
            bank.refresh(balances);
        } else {
            client.setScreen(new BankScreen(balances));
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
}
