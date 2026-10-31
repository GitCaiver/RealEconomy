package com.realeconomy.market;

import com.realeconomy.item.ModItems;
import com.realeconomy.network.MarketActionPayload;
import com.realeconomy.network.MarketOfferView;
import com.realeconomy.network.OpenMarketPayload;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de servidor de los mercados (parte 6, "solo vender"): el mercado <b>compra</b>
 * items a los jugadores pagando monedas <b>físicas</b> de su caja, a precio dinámico
 * por oferta y demanda.
 */
public final class MarketActions {

    private MarketActions() {
    }

    /** Clic derecho sobre un NPC de mercado. */
    public static void openForEntity(ServerPlayerEntity player, VillagerEntity npc) {
        String marketId = MarketEntities.marketIdOf(npc);
        if (marketId == null) {
            return;
        }
        Market market = MarketManager.get(player.getServer()).getById(marketId);
        if (market == null) {
            player.sendMessage(Text.literal("Este mercado ya no existe.").formatted(Formatting.RED));
            return;
        }
        Group group = GroupManager.get(player.getServer()).getByPlayer(player.getUuid());
        boolean member = group != null && group.getId().equals(market.getGroupId());
        if (member) {
            openTill(player, market);
        } else {
            openCustomerView(player, market);
        }
    }

    /** Abre la caja (chest vanilla) para un miembro del grupo. */
    public static void openTill(ServerPlayerEntity player, Market market) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, market.getStorage()),
                Text.literal("Caja · Mercado [" + market.getGroupTag() + "]")));
    }

    public static void openCustomerView(ServerPlayerEntity player, Market market) {
        ServerPlayNetworking.send(player, new OpenMarketPayload(market.getId(),
                "[" + market.getGroupTag() + "] " + market.getGroupName(), buildViews(market)));
    }

    public static List<MarketOfferView> buildViews(Market market) {
        List<MarketOfferView> views = new ArrayList<>();
        List<MarketOffer> offers = market.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            MarketOffer o = offers.get(i);
            long price = o.currentPrice();
            int coins = tillCoinCount(market, o.getCurrencyId());
            int available = price <= 0 ? coins : (int) (coins / price);
            int trend = switch (o.trend()) {
                case UP -> MarketOfferView.UP;
                case DOWN -> MarketOfferView.DOWN;
                default -> MarketOfferView.STABLE;
            };
            views.add(new MarketOfferView(i, o.getTemplate().copy(), price, o.getBasePrice(),
                    o.getCurrencyId(), available, trend));
        }
        return views;
    }

    public static void handle(ServerPlayerEntity player, MarketActionPayload action) {
        Market market = MarketManager.get(player.getServer()).getById(action.marketId());
        if (market == null) {
            return;
        }
        if (!MarketEntities.isNearMarket(player, market.getId())) {
            player.sendMessage(Text.literal("No hay ningún mercado cerca.").formatted(Formatting.RED));
            return;
        }
        List<MarketOffer> offers = market.getOffers();
        if (action.offerIndex() < 0 || action.offerIndex() >= offers.size()) {
            openCustomerView(player, market);
            return;
        }
        MarketOffer offer = offers.get(action.offerIndex());
        int amount = Math.max(1, action.amount());
        sellToMarket(player, market, offer, amount);
        openCustomerView(player, market);
    }

    // ---- Cliente VENDE al mercado: entrega item, recibe monedas de la caja ----

    private static void sellToMarket(ServerPlayerEntity player, Market market, MarketOffer offer, int amount) {
        ItemStack template = offer.getTemplate();
        String currencyId = offer.getCurrencyId();
        long price = offer.currentPrice();
        if (price <= 0) {
            player.sendMessage(Text.literal("Esta oferta no tiene precio.").formatted(Formatting.RED));
            return;
        }

        int have = itemsInInventory(player, template);
        int tillCoins = tillCoinCount(market, currencyId);
        int byCoins = (int) Math.min(amount, tillCoins / price);
        int itemSpace = tillItemSpace(market, template);
        int possible = Math.min(amount, Math.min(have, Math.min(byCoins, itemSpace)));
        if (possible <= 0) {
            String reason = have <= 0 ? "No tienes ese item."
                    : (byCoins <= 0 ? "El mercado no tiene monedas para pagar." : "La caja del mercado está llena.");
            player.sendMessage(Text.literal(reason).formatted(Formatting.RED));
            return;
        }
        long totalCoins = possible * price;
        // Mete el item del cliente en la caja.
        removeItemFromInventory(player, template, possible);
        addItemToTill(market, template, possible);
        // Paga al cliente con monedas de la caja.
        removeCoinsFromTill(market, currencyId, (int) totalCoins);
        giveCoins(player, currencyId, (int) totalCoins);
        // Oferta y demanda: el precio baja al vender.
        offer.recordSale(possible);
        player.sendMessage(Text.literal("Vendido x" + possible + " por " + totalCoins + " monedas.").formatted(Formatting.GREEN));
        MarketManager.get(player.getServer()).markDirty();
    }

    // ---- Helpers: items en la caja ----

    private static void addItemToTill(Market market, ItemStack template, int count) {
        market.getStorage().addStack(template.copyWithCount(count));
    }

    /** Huecos (en unidades) que la caja puede aceptar del template dado. */
    private static int tillItemSpace(Market market, ItemStack template) {
        SimpleInventory till = market.getStorage();
        int max = template.getMaxCount();
        int space = 0;
        for (int i = 0; i < till.size(); i++) {
            ItemStack s = till.getStack(i);
            if (s.isEmpty()) {
                space += max;
            } else if (ItemStack.areItemsAndComponentsEqual(s, template)) {
                space += Math.max(0, max - s.getCount());
            }
        }
        return space;
    }

    // ---- Helpers: monedas en la caja ----

    private static int tillCoinCount(Market market, String currencyId) {
        SimpleInventory till = market.getStorage();
        int total = 0;
        for (int i = 0; i < till.size(); i++) {
            ItemStack s = till.getStack(i);
            if (currencyId.equals(ModItems.currencyOf(s))) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void removeCoinsFromTill(Market market, String currencyId, int count) {
        SimpleInventory till = market.getStorage();
        int remaining = count;
        for (int i = 0; i < till.size() && remaining > 0; i++) {
            ItemStack s = till.getStack(i);
            if (currencyId.equals(ModItems.currencyOf(s))) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
        till.markDirty();
    }

    // ---- Helpers: inventario del jugador ----

    private static int itemsInInventory(ServerPlayerEntity player, ItemStack template) {
        PlayerInventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (ItemStack.areItemsAndComponentsEqual(s, template)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void removeItemFromInventory(ServerPlayerEntity player, ItemStack template, int count) {
        PlayerInventory inv = player.getInventory();
        int remaining = count;
        for (int i = 0; i < inv.size() && remaining > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (ItemStack.areItemsAndComponentsEqual(s, template)) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
    }

    private static void giveCoins(ServerPlayerEntity player, String currencyId, int count) {
        int remaining = count;
        while (remaining > 0) {
            int n = Math.min(64, remaining);
            player.giveItemStack(ModItems.createCoin(currencyId, n));
            remaining -= n;
        }
    }
}
