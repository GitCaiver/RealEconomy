package com.realeconomy.employment;

import com.realeconomy.compat.mca.Mca;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.economy.MonetaryManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.network.BookRow;
import com.realeconomy.network.LibrarianActionPayload;
import com.realeconomy.network.OpenLibrarianPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lógica de servidor del librero: muestra el stock de libros encantados y procesa las compras. */
public final class LibrarianActions {

    private LibrarianActions() {
    }

    /** ¿Es este aldeano un librero empleado (para abrirle la tienda de libros al cliente)? */
    public static boolean isLibrarian(MinecraftServer server, Entity villager) {
        if (!Mca.isMcaVillager(villager)) {
            return false;
        }
        EmployedVillager ev = EmploymentManager.get(server).get(villager.getUuid());
        return ev != null && ev.job() == VillagerJob.LIBRERO;
    }

    public static void openFor(ServerPlayerEntity player, Entity villager) {
        MinecraftServer server = player.getServer();
        if (server == null || !isLibrarian(server, villager)) {
            return;
        }
        OpenLibrarianPayload payload = buildPayload(server, villager.getUuid(), villager.getName().getString());
        if (payload != null) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static OpenLibrarianPayload buildPayload(MinecraftServer server, UUID villagerId, String name) {
        EmployedVillager ev = EmploymentManager.get(server).get(villagerId);
        if (ev == null) {
            return null;
        }
        Group country = GroupManager.get(server).getById(ev.countryId());
        Currency currency = country == null ? null : CurrencyManager.get(server).getByGroup(country.getId());
        if (currency == null) {
            return null;
        }
        MonetaryManager mm = MonetaryManager.get(server);
        LibrarianManager lm = LibrarianManager.get(server);
        List<LibrarianManager.StockEntry> stock = lm.stock(villagerId);
        List<BookRow> rows = new ArrayList<>();
        for (int i = 0; i < stock.size(); i++) {
            LibrarianManager.StockEntry e = stock.get(i);
            ItemStack book = LibrarianManager.buildBook(server, e.enchant, e.level);
            if (book == null) {
                continue;
            }
            long price = Math.max(1, mm.coinsForEmeralds(currency.getId(), e.priceEmeralds));
            rows.add(new BookRow(i, book, price, e.units));
        }
        long secs = Math.max(0, (lm.nextRotation(villagerId) - System.currentTimeMillis()) / 1000L);
        return new OpenLibrarianPayload(villagerId, name, currency.getName(), secs, rows);
    }

    /** Vista de ejemplo del librero para /ecoadmin debug librero (sin aldeano real). */
    public static void debugOpen(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        Currency currency = g == null ? null : CurrencyManager.get(server).getByGroup(g.getId());
        String currencyName = currency == null ? "demo" : currency.getName();
        MonetaryManager mm = MonetaryManager.get(server);
        List<BookRow> rows = new ArrayList<>();
        var books = ModConfig.get().librarianBooks;
        int n = Math.min(ModConfig.get().librarianBooksPerCycle, books.size());
        for (int i = 0; i < n; i++) {
            ItemStack book = LibrarianManager.buildBook(server, books.get(i).enchant, books.get(i).level);
            if (book == null) {
                continue;
            }
            long price = currency == null ? (long) books.get(i).price
                    : Math.max(1, mm.coinsForEmeralds(currency.getId(), books.get(i).price));
            rows.add(new BookRow(i, book, price, ModConfig.get().librarianUnitsPerBook));
        }
        ServerPlayNetworking.send(player, new OpenLibrarianPayload(player.getUuid(),
                "Librero de ejemplo", currencyName, 86_400L, rows));
    }

    public static void handleBuy(ServerPlayerEntity player, LibrarianActionPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Entity entity = player.getServerWorld().getEntity(payload.villager());
        if (entity == null || !isLibrarian(server, entity)) {
            player.sendMessage(Text.literal("Este librero ya no está disponible.").formatted(Formatting.RED), false);
            return;
        }
        EmployedVillager ev = EmploymentManager.get(server).get(payload.villager());
        Group country = GroupManager.get(server).getById(ev.countryId());
        Currency currency = country == null ? null : CurrencyManager.get(server).getByGroup(country.getId());
        if (currency == null) {
            return;
        }
        LibrarianManager lm = LibrarianManager.get(server);
        List<LibrarianManager.StockEntry> stock = lm.stock(payload.villager());
        if (payload.index() < 0 || payload.index() >= stock.size()) {
            return;
        }
        LibrarianManager.StockEntry entry = stock.get(payload.index());
        if (entry.units <= 0) {
            player.sendMessage(Text.literal("Ese libro está agotado este ciclo.").formatted(Formatting.YELLOW), false);
            return;
        }
        ItemStack book = LibrarianManager.buildBook(server, entry.enchant, entry.level);
        if (book == null) {
            return;
        }
        long price = Math.max(1, MonetaryManager.get(server).coinsForEmeralds(currency.getId(), entry.priceEmeralds));

        BalanceManager balances = BalanceManager.get(server);
        if (!balances.subtract(player.getUuid(), currency.getId(), price)) {
            player.sendMessage(Text.literal("No tienes " + price + " " + currency.getName() + ".")
                    .formatted(Formatting.RED), false);
            return;
        }

        // Impuesto de venta de tienda del país: el corte va a la cuenta del país; el resto, al recaudador.
        int pct = FinanceManager.get(server).getPolicy(country.getId()).shopSalePercent;
        long net = FinanceManager.get(server).applyTransactionTax(server, country, pct, price, currency.getId());
        UUID recipient = ModConfig.get().librarianRevenueToCountry
                ? FinanceManager.accountUuid(country) : ev.employer();
        balances.add(recipient, currency.getId(), net);

        entry.units--;
        lm.markDirty();
        player.getInventory().offerOrDrop(book);
        player.sendMessage(Text.literal("Compraste un libro encantado por " + price + " " + currency.getName() + ".")
                .formatted(Formatting.GREEN), false);

        OpenLibrarianPayload refreshed = buildPayload(server, payload.villager(), entity.getName().getString());
        if (refreshed != null) {
            ServerPlayNetworking.send(player, refreshed);
        }
    }
}
