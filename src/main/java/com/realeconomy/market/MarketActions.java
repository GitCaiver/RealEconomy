package com.realeconomy.market;

import com.realeconomy.item.ModItems;
import com.realeconomy.network.MarketActionPayload;
import com.realeconomy.network.OfferView;
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
 * Lógica de servidor de los mercados de grupo. Las transacciones usan monedas
 * <b>físicas</b> que entran/salen de la caja del mercado.
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

    public static List<OfferView> buildViews(Market market) {
        List<OfferView> views = new ArrayList<>();
        List<MarketOffer> offers = market.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            MarketOffer o = offers.get(i);
            int available;
            if (o.getType() == MarketOffer.Type.SELL) {
                available = tillItemCount(market, o.getTemplate());
            } else {
                int coins = tillCoinCount(market, o.getCurrencyId());
                available = o.getPrice() <= 0 ? coins : (int) (coins / o.getPrice());
            }
            int type = o.getType() == MarketOffer.Type.SELL ? OfferView.SELL : OfferView.BUY;
            views.add(new OfferView(i, type, o.getTemplate().copy(), o.getPrice(), o.getCurrencyId(), available));
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

        if (action.action() == MarketActionPayload.BUY && offer.getType() == MarketOffer.Type.SELL) {
            buyFromMarket(player, market, offer, amount);
        } else if (action.action() == MarketActionPayload.SELL && offer.getType() == MarketOffer.Type.BUY) {
            sellToMarket(player, market, offer, amount);
        }
        openCustomerView(player, market);
    }

    // ---- Cliente COMPRA al mercado (oferta de VENTA): paga monedas, recibe item ----

    private static void buyFromMarket(ServerPlayerEntity player, Market market, MarketOffer offer, int amount) {
        ItemStack template = offer.getTemplate();
        String currencyId = offer.getCurrencyId();
        long price = offer.getPrice();

        int stock = tillItemCount(market, template);
        int playerCoins = coinsInInventory(player, currencyId);
        int byCoins = price <= 0 ? amount : (int) Math.min(amount, playerCoins / price);
        // La caja debe poder aceptar las monedas del pago.
        int coinSpace = tillCoinSpace(market, currencyId);
        int bySpace = price <= 0 ? amount : (int) (coinSpace / price);
        int possible = Math.min(amount, Math.min(stock, Math.min(byCoins, bySpace)));
        if (possible <= 0) {
            String reason = stock <= 0 ? "Sin stock en el mercado."
                    : (byCoins <= 0 ? "No tienes monedas suficientes." : "La caja del mercado está llena.");
            player.sendMessage(Text.literal(reason).formatted(Formatting.RED));
            return;
        }
        long totalCoins = possible * price;
        // Cobra las monedas del cliente a la caja.
        removeCoinsFromInventory(player, currencyId, (int) totalCoins);
        addCoinsToTill(market, currencyId, (int) totalCoins);
        // Entrega el item del stock de la caja.
        int removed = removeItemFromTill(market, template, possible);
        giveStack(player, template, removed);
        player.sendMessage(Text.literal("Comprado x" + removed + " por " + totalCoins + " monedas.").formatted(Formatting.GREEN));
        MarketManager.get(player.getServer()).markDirty();
    }

    // ---- Cliente VENDE al mercado (oferta de COMPRA): entrega item, recibe monedas ----

    private static void sellToMarket(ServerPlayerEntity player, Market market, MarketOffer offer, int amount) {
        ItemStack template = offer.getTemplate();
        String currencyId = offer.getCurrencyId();
        long price = offer.getPrice();

        int have = itemsInInventory(player, template);
        int tillCoins = tillCoinCount(market, currencyId);
        int byCoins = price <= 0 ? amount : (int) Math.min(amount, tillCoins / price);
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
        player.sendMessage(Text.literal("Vendido x" + possible + " por " + totalCoins + " monedas.").formatted(Formatting.GREEN));
        MarketManager.get(player.getServer()).markDirty();
    }

    // ---- Helpers: items en la caja ----

    private static int tillItemCount(Market market, ItemStack template) {
        SimpleInventory till = market.getStorage();
        int total = 0;
        for (int i = 0; i < till.size(); i++) {
            ItemStack s = till.getStack(i);
            if (ItemStack.areItemsAndComponentsEqual(s, template)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static int removeItemFromTill(Market market, ItemStack template, int count) {
        SimpleInventory till = market.getStorage();
        int remaining = count;
        for (int i = 0; i < till.size() && remaining > 0; i++) {
            ItemStack s = till.getStack(i);
            if (ItemStack.areItemsAndComponentsEqual(s, template)) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
        till.markDirty();
        return count - remaining;
    }

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

    private static int tillCoinSpace(Market market, String currencyId) {
        return tillItemSpace(market, ModItems.createCoin(currencyId, 1));
    }

    private static void addCoinsToTill(Market market, String currencyId, int count) {
        int remaining = count;
        while (remaining > 0) {
            int n = Math.min(64, remaining);
            market.getStorage().addStack(ModItems.createCoin(currencyId, n));
            remaining -= n;
        }
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

    private static int coinsInInventory(ServerPlayerEntity player, String currencyId) {
        PlayerInventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (currencyId.equals(ModItems.currencyOf(s))) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void removeCoinsFromInventory(ServerPlayerEntity player, String currencyId, int count) {
        PlayerInventory inv = player.getInventory();
        int remaining = count;
        for (int i = 0; i < inv.size() && remaining > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (currencyId.equals(ModItems.currencyOf(s))) {
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

    private static void giveStack(ServerPlayerEntity player, ItemStack template, int count) {
        int max = template.getMaxCount();
        int remaining = count;
        while (remaining > 0) {
            int n = Math.min(max, remaining);
            player.giveItemStack(template.copyWithCount(n));
            remaining -= n;
        }
    }
}
