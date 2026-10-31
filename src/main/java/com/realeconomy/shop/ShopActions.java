package com.realeconomy.shop;

import com.realeconomy.economy.BalanceManager;
import com.realeconomy.network.OfferView;
import com.realeconomy.network.OpenShopPayload;
import com.realeconomy.network.ShopActionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Lógica de servidor de las tiendas: apertura de interfaces y compra/venta. */
public final class ShopActions {

    private ShopActions() {
    }

    /** Clic derecho sobre un NPC de tienda. */
    public static void openForEntity(ServerPlayerEntity player, VillagerEntity npc) {
        String shopId = ShopEntities.shopIdOf(npc);
        if (shopId == null) {
            return;
        }
        Shop shop = ShopManager.get(player.getServer()).getById(shopId);
        if (shop == null) {
            player.sendMessage(Text.literal("Esta tienda ya no existe.").formatted(Formatting.RED));
            return;
        }
        if (shop.getOwner().equals(player.getUuid())) {
            openStorage(player, shop);
        } else {
            openCustomerView(player, shop);
        }
    }

    /** Abre el almacén (chest vanilla) para el dueño. */
    public static void openStorage(ServerPlayerEntity player, Shop shop) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, shop.getStorage()),
                Text.literal("Almacén · " + shop.getOwnerName())));
    }

    public static void openCustomerView(ServerPlayerEntity player, Shop shop) {
        ServerPlayNetworking.send(player, new OpenShopPayload(shop.getId(), shop.getOwnerName(), buildViews(shop)));
    }

    public static List<OfferView> buildViews(Shop shop) {
        List<OfferView> views = new ArrayList<>();
        List<Offer> offers = shop.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            Offer o = offers.get(i);
            int available = o.getType() == Offer.Type.SELL
                    ? storageCount(shop, o.getTemplate())
                    : o.getRemaining();
            int type = o.getType() == Offer.Type.SELL ? OfferView.SELL : OfferView.BUY;
            views.add(new OfferView(i, type, o.getTemplate().copy(), o.getPrice(), o.getCurrencyId(), available));
        }
        return views;
    }

    public static void handle(ServerPlayerEntity player, ShopActionPayload action) {
        ShopManager mgr = ShopManager.get(player.getServer());
        Shop shop = mgr.getById(action.shopId());
        if (shop == null) {
            return;
        }
        if (!ShopEntities.isNearShop(player, shop.getId())) {
            player.sendMessage(Text.literal("No hay ninguna tienda cerca.").formatted(Formatting.RED));
            return;
        }
        List<Offer> offers = shop.getOffers();
        if (action.offerIndex() < 0 || action.offerIndex() >= offers.size()) {
            openCustomerView(player, shop);
            return;
        }
        Offer offer = offers.get(action.offerIndex());
        int amount = Math.max(1, action.amount());

        if (action.action() == ShopActionPayload.BUY && offer.getType() == Offer.Type.SELL) {
            buyFromShop(player, mgr, shop, offer, amount);
        } else if (action.action() == ShopActionPayload.SELL && offer.getType() == Offer.Type.BUY) {
            sellToShop(player, mgr, shop, offer, amount);
        }
        openCustomerView(player, shop);
    }

    // ---- Comprar (cliente compra de una oferta de VENTA) ----

    private static void buyFromShop(ServerPlayerEntity player, ShopManager mgr, Shop shop, Offer offer, int amount) {
        MinecraftServer server = player.getServer();
        BalanceManager bm = BalanceManager.get(server);
        ItemStack template = offer.getTemplate();
        int stock = storageCount(shop, template);
        long balance = bm.get(player.getUuid(), offer.getCurrencyId());
        int byBalance = offer.getPrice() <= 0 ? amount : (int) Math.min(amount, balance / offer.getPrice());
        int possible = Math.min(amount, Math.min(stock, byBalance));
        if (possible <= 0) {
            player.sendMessage(Text.literal(stock <= 0 ? "Sin stock." : "Saldo insuficiente.").formatted(Formatting.RED));
            return;
        }
        int removed = removeFromStorage(shop, template, possible);
        long total = (long) removed * offer.getPrice();
        bm.subtract(player.getUuid(), offer.getCurrencyId(), total);
        // Impuesto de venta de tienda (parte 4): el país del dueño retiene un % de la venta.
        var ownerCountry = com.realeconomy.group.GroupManager.get(server).getByPlayer(shop.getOwner());
        long tax = 0L;
        if (ownerCountry != null) {
            int pct = com.realeconomy.finance.FinanceManager.get(server).getPolicy(ownerCountry.getId()).shopSalePercent;
            tax = com.realeconomy.finance.FinanceManager.get(server)
                    .applyTransactionTax(server, ownerCountry, pct, total, offer.getCurrencyId());
        }
        bm.add(shop.getOwner(), offer.getCurrencyId(), total - tax);
        giveStack(player, template, removed);
        player.sendMessage(Text.literal("Comprado x" + removed + " por " + total
                + (tax > 0 ? " (impuesto " + tax + " al país del vendedor)" : "") + ".").formatted(Formatting.GREEN));

        if (storageCount(shop, template) <= 0) {
            shop.getOffers().remove(offer);
            player.sendMessage(Text.literal("La oferta se agotó y se retiró.").formatted(Formatting.YELLOW));
        }
        mgr.markDirty();
    }

    // ---- Vender (cliente vende a una oferta de COMPRA) ----

    private static void sellToShop(ServerPlayerEntity player, ShopManager mgr, Shop shop, Offer offer, int amount) {
        MinecraftServer server = player.getServer();
        BalanceManager bm = BalanceManager.get(server);
        ItemStack template = offer.getTemplate();
        int have = countInInventory(player, template);
        long ownerBalance = bm.get(shop.getOwner(), offer.getCurrencyId());
        int byOwner = offer.getPrice() <= 0 ? amount : (int) Math.min(amount, ownerBalance / offer.getPrice());
        int possible = Math.min(amount, Math.min(offer.getRemaining(), Math.min(have, byOwner)));
        if (possible <= 0) {
            String reason = have <= 0 ? "No tienes ese item."
                    : (byOwner <= 0 ? "El dueño no tiene saldo." : "No se puede vender más.");
            player.sendMessage(Text.literal(reason).formatted(Formatting.RED));
            return;
        }
        // Mete en el almacén lo que quepa.
        ItemStack moving = template.copyWithCount(possible);
        ItemStack leftover = shop.getStorage().addStack(moving);
        int inserted = possible - leftover.getCount();
        if (inserted <= 0) {
            player.sendMessage(Text.literal("El almacén de la tienda está lleno.").formatted(Formatting.RED));
            return;
        }
        removeFromInventory(player, template, inserted);
        long total = (long) inserted * offer.getPrice();
        bm.subtract(shop.getOwner(), offer.getCurrencyId(), total);
        bm.add(player.getUuid(), offer.getCurrencyId(), total);
        offer.setRemaining(offer.getRemaining() - inserted);
        player.sendMessage(Text.literal("Vendido x" + inserted + " por " + total + ".").formatted(Formatting.GREEN));

        long newOwnerBalance = bm.get(shop.getOwner(), offer.getCurrencyId());
        if (offer.getRemaining() <= 0 || (offer.getPrice() > 0 && newOwnerBalance < offer.getPrice())) {
            shop.getOffers().remove(offer);
            player.sendMessage(Text.literal("La oferta de compra se completó y se retiró.").formatted(Formatting.YELLOW));
        }
        mgr.markDirty();
    }

    // ---- Helpers de inventario/almacén ----

    private static int storageCount(Shop shop, ItemStack template) {
        int total = 0;
        for (int i = 0; i < shop.getStorage().size(); i++) {
            ItemStack s = shop.getStorage().getStack(i);
            if (ItemStack.areItemsAndComponentsEqual(s, template)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static int removeFromStorage(Shop shop, ItemStack template, int count) {
        int remaining = count;
        for (int i = 0; i < shop.getStorage().size() && remaining > 0; i++) {
            ItemStack s = shop.getStorage().getStack(i);
            if (ItemStack.areItemsAndComponentsEqual(s, template)) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
        shop.getStorage().markDirty();
        return count - remaining;
    }

    private static int countInInventory(ServerPlayerEntity player, ItemStack template) {
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

    private static void removeFromInventory(ServerPlayerEntity player, ItemStack template, int count) {
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
