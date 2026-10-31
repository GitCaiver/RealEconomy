package com.realeconomy.buyer;

import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.network.BuyerActionPayload;
import com.realeconomy.network.BuyerCategoryView;
import com.realeconomy.network.OpenBuyerPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Lógica de servidor de los NPC compradores por categoría (parte 4). */
public final class BuyerActions {

    private BuyerActions() {
    }

    /** País al que pertenece un NPC comprador (según su tag), o null. */
    public static Group countryOf(MinecraftServer server, VillagerEntity npc) {
        String id = BuyerEntities.countryIdOf(npc);
        return id == null ? null : GroupManager.get(server).getById(id);
    }

    public static void openForEntity(ServerPlayerEntity player, VillagerEntity npc) {
        MinecraftServer server = player.getServer();
        Group country = countryOf(server, npc);
        if (country == null) {
            player.sendMessage(Text.literal("Este comprador no tiene país.").formatted(Formatting.RED));
            return;
        }
        Currency c = CurrencyManager.get(server).getByGroup(country.getId());
        if (c == null) {
            player.sendMessage(Text.literal("El país de este comprador no tiene moneda.").formatted(Formatting.RED));
            return;
        }
        sendPanel(player, server, country, c);
    }

    private static void sendPanel(ServerPlayerEntity player, MinecraftServer server, Group country, Currency c) {
        BuyerMarketManager mgr = BuyerMarketManager.get(server);
        java.util.List<BuyerCategoryView> rows = new java.util.ArrayList<>();
        for (String cat : ModConfig.get().buyerCategories.keySet()) {
            double em = mgr.categoryEmeraldPrice(cat);
            long coins = BuyerMarketManager.toCoins(server, c.getId(), em);
            rows.add(new BuyerCategoryView(cat, coins, mgr.multiplier(cat)));
        }
        ServerPlayNetworking.send(player, new OpenBuyerPayload(
                "Comprador [" + country.getTag() + "]", c.getName(), rows));
    }

    public static void handle(ServerPlayerEntity player, BuyerActionPayload action) {
        MinecraftServer server = player.getServer();
        VillagerEntity npc = BuyerEntities.nearest(player);
        if (npc == null) {
            player.sendMessage(Text.literal("No hay ningún comprador cerca.").formatted(Formatting.RED));
            return;
        }
        Group country = countryOf(server, npc);
        Currency c = country == null ? null : CurrencyManager.get(server).getByGroup(country.getId());
        if (country == null || c == null) {
            player.sendMessage(Text.literal("Este comprador no está operativo.").formatted(Formatting.RED));
            return;
        }
        if (action.action() == BuyerActionPayload.SELL_INVENTORY) {
            sellInventory(server, country, c, player);
        }
        sendPanel(player, server, country, c);
    }

    /** Vende al país todo lo comprable del inventario del jugador (hasta donde haya fondos). */
    public static void sellInventory(MinecraftServer server, Group country, Currency c, ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        Set<Item> items = new LinkedHashSet<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && BuyerCategories.categoryOf(s.getItem()) != null) {
                items.add(s.getItem());
            }
        }
        long totalCoins = 0L;
        int totalUnits = 0;
        for (Item item : items) {
            long[] r = sellItemType(server, country, c.getId(), player, item, Integer.MAX_VALUE);
            totalUnits += (int) r[0];
            totalCoins += r[1];
        }
        if (totalUnits <= 0) {
            player.sendMessage(Text.literal("No se vendió nada (nada comprable o el país no tiene fondos).")
                    .formatted(Formatting.YELLOW), false);
            return;
        }
        final long paid = totalCoins;
        final int units = totalUnits;
        player.sendMessage(Text.literal("Vendidas " + units + " unidades por " + paid + " " + c.getName() + ".")
                .formatted(Formatting.GREEN), false);
    }

    /**
     * Vende hasta {@code maxUnits} del item indicado del inventario del jugador al país.
     * Devuelve {unidades vendidas, monedas pagadas}.
     */
    public static long[] sellItemType(MinecraftServer server, Group country, String currencyId,
                                      ServerPlayerEntity player, Item item, int maxUnits) {
        String category = BuyerCategories.categoryOf(item);
        if (category == null) {
            return new long[]{0, 0};
        }
        PlayerInventory inv = player.getInventory();
        int have = countItem(inv, item);
        if (have <= 0) {
            return new long[]{0, 0};
        }
        BuyerMarketManager mgr = BuyerMarketManager.get(server);
        long unit = mgr.coinPrice(server, currencyId, item);
        if (unit <= 0) {
            return new long[]{0, 0};
        }
        BalanceManager bm = BalanceManager.get(server);
        UUID account = FinanceManager.accountUuid(country);
        long funds = bm.get(account, currencyId);
        long affordable = funds / unit;
        int units = (int) Math.min(Math.min(have, maxUnits), affordable);
        if (units <= 0) {
            return new long[]{0, 0};
        }
        long pay = (long) units * unit;
        if (!bm.subtract(account, currencyId, pay)) {
            return new long[]{0, 0};
        }
        removeItem(inv, item, units);
        // Impuesto de venta a NPC de mercado (parte 4): el país del vendedor retiene un %.
        var sellerCountry = GroupManager.get(server).getByPlayer(player.getUuid());
        long tax = 0L;
        if (sellerCountry != null) {
            int pct = FinanceManager.get(server).getPolicy(sellerCountry.getId()).marketSalePercent;
            tax = FinanceManager.get(server).applyTransactionTax(server, sellerCountry, pct, pay, currencyId);
        }
        bm.add(player.getUuid(), currencyId, pay - tax);
        mgr.recordSale(category, units);
        return new long[]{units, pay - tax};
    }

    // ---- Inventario ----

    private static int countItem(PlayerInventory inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void removeItem(PlayerInventory inv, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) {
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
            }
        }
        inv.markDirty();
    }
}
