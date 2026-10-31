package com.realeconomy.bank;

import com.mojang.authlib.GameProfile;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.item.ModItems;
import com.realeconomy.jobs.Job;
import com.realeconomy.jobs.Jobs;
import com.realeconomy.network.BankActionPayload;
import com.realeconomy.network.OpenBankPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lógica de servidor del banco. */
public final class BankActions {

    private BankActions() {
    }

    /** Abre la GUI del banco para el jugador (envía sus saldos y las monedas con su valor). */
    public static void openFor(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        Map<String, Long> balances = new LinkedHashMap<>(BalanceManager.get(server).getAll(player.getUuid()));
        ServerPlayNetworking.send(player, new OpenBankPayload(
                balances, currencyInfos(server), ModConfig.get().bankExchangeCommissionPercent));
    }

    /** Lista de todas las monedas con su valor actual (para el cambio de moneda en la GUI). */
    private static java.util.List<com.realeconomy.network.BankCurrencyInfo> currencyInfos(MinecraftServer server) {
        java.util.List<com.realeconomy.network.BankCurrencyInfo> list = new java.util.ArrayList<>();
        var gm = com.realeconomy.group.GroupManager.get(server);
        var mm = com.realeconomy.economy.MonetaryManager.get(server);
        var fm = com.realeconomy.finance.FinanceManager.get(server);
        for (Currency c : CurrencyManager.get(server).all()) {
            var g = gm.getById(c.getId());
            String tag = g != null ? g.getTag() : "";
            String color = g != null && g.getColor() != null ? g.getColor().getName() : "gold";
            list.add(new com.realeconomy.network.BankCurrencyInfo(c.getId(), c.getName(), tag, color,
                    mm.value(c.getId()), fm.effectiveExchangePercent(c.getId())));
        }
        return list;
    }

    public static void handle(ServerPlayerEntity player, BankActionPayload action) {
        if (!BankEntities.isNearBank(player)) {
            player.sendMessage(Text.literal("No hay ningún banco cerca.").formatted(Formatting.RED));
            return;
        }
        switch (action.action()) {
            case BankActionPayload.DEPOSIT -> deposit(player, action.currencyId(), action.amount());
            case BankActionPayload.WITHDRAW -> withdraw(player, action.currencyId(), action.amount());
            case BankActionPayload.TRANSFER -> transfer(player, action.currencyId(), action.amount(), action.target());
            case BankActionPayload.EXCHANGE -> exchange(player, action.currencyId(), action.target(), action.amount());
            default -> {
            }
        }
        openFor(player); // refresca la GUI con los saldos actualizados
    }

    /** Resultado de una cotización de cambio: cuánto se recibe y la comisión, en moneda destino. */
    public record Quote(long net, long commission, boolean ok, String error) {
    }

    /**
     * Cotiza un cambio de {@code amount} monedas de {@code originId} a {@code destId} usando el valor
     * actual de cada moneda (inflación/devaluación) y la comisión configurada. No mueve saldo.
     */
    public static Quote quote(MinecraftServer server, String originId, String destId, long amount) {
        if (originId == null || destId == null || originId.equals(destId)) {
            return new Quote(0, 0, false, "Elige dos monedas distintas.");
        }
        if (amount <= 0) {
            return new Quote(0, 0, false, "Indica una cantidad válida.");
        }
        var mm = com.realeconomy.economy.MonetaryManager.get(server);
        double vo = mm.value(originId);
        double vd = mm.value(destId);
        if (vo <= 0 || vd <= 0) {
            return new Quote(0, 0, false, "Alguna moneda no tiene valor.");
        }
        double emeralds = amount * vo;
        long gross = (long) Math.floor(emeralds / vd);
        // Comisión = % de cambio efectivo del país que emite la moneda destino (política o config).
        int pct = com.realeconomy.finance.FinanceManager.get(server).effectiveExchangePercent(destId);
        long commission = (long) Math.floor(gross * pct / 100.0);
        long net = gross - commission;
        if (net <= 0) {
            return new Quote(0, 0, false, "La cantidad es demasiado baja para este cambio.");
        }
        return new Quote(net, commission, true, null);
    }

    /** Cambia moneda del jugador (origen→destino) con comisión a la cuenta del país destino. */
    public static void exchange(ServerPlayerEntity player, String originId, String destId, long amount) {
        MinecraftServer server = player.getServer();
        Quote q = quote(server, originId, destId, amount);
        if (!q.ok()) {
            player.sendMessage(Text.literal(q.error()).formatted(Formatting.RED));
            return;
        }
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(player.getUuid(), originId, amount)) {
            player.sendMessage(Text.literal("Saldo insuficiente de la moneda de origen.").formatted(Formatting.RED));
            return;
        }
        bm.add(player.getUuid(), destId, q.net());
        // La comisión va a la cuenta del país que emite la moneda destino.
        if (q.commission() > 0) {
            var g = com.realeconomy.group.GroupManager.get(server).getById(destId);
            if (g != null) {
                bm.add(com.realeconomy.finance.FinanceManager.accountUuid(g), destId, q.commission());
            }
        }
        Currency cd = CurrencyManager.get(server).getById(destId);
        Currency co = CurrencyManager.get(server).getById(originId);
        String dn = cd != null ? cd.getName() : destId;
        String on = co != null ? co.getName() : originId;
        player.sendMessage(Text.literal("Cambiadas " + amount + " " + on + " → " + q.net() + " " + dn
                + " (comisión " + q.commission() + ").").formatted(Formatting.GREEN));
    }

    private static void deposit(ServerPlayerEntity player, String currencyId, long amount) {
        int available = countCoins(player, currencyId);
        if (available <= 0) {
            player.sendMessage(Text.literal("No tienes monedas de esa divisa.").formatted(Formatting.RED));
            return;
        }
        int toDeposit = amount <= 0 ? available : (int) Math.min(amount, available);
        int removed = removeCoins(player, currencyId, toDeposit);
        if (removed > 0) {
            BalanceManager.get(player.getServer()).add(player.getUuid(), currencyId, removed);
            player.sendMessage(Text.literal("Ingresadas " + removed + " monedas.").formatted(Formatting.GREEN));
            Jobs.payWage(player, Job.BANKING);
        }
    }

    private static void withdraw(ServerPlayerEntity player, String currencyId, long amount) {
        if (amount <= 0) {
            return;
        }
        BalanceManager bm = BalanceManager.get(player.getServer());
        if (!bm.subtract(player.getUuid(), currencyId, amount)) {
            player.sendMessage(Text.literal("Saldo insuficiente.").formatted(Formatting.RED));
            return;
        }
        giveCoins(player, currencyId, (int) amount);
        player.sendMessage(Text.literal("Retiradas " + amount + " monedas.").formatted(Formatting.GREEN));
        Jobs.payWage(player, Job.BANKING);
    }

    private static void transfer(ServerPlayerEntity player, String currencyId, long amount, String target) {
        if (amount <= 0 || target == null || target.isBlank()) {
            player.sendMessage(Text.literal("Indica un jugador y una cantidad válida.").formatted(Formatting.RED));
            return;
        }
        MinecraftServer server = player.getServer();
        Optional<GameProfile> profile = server.getUserCache() == null
                ? Optional.empty()
                : server.getUserCache().findByName(target);
        if (profile.isEmpty()) {
            player.sendMessage(Text.literal("No se encuentra al jugador '" + target + "'.").formatted(Formatting.RED));
            return;
        }
        UUID targetId = profile.get().getId();
        if (targetId.equals(player.getUuid())) {
            player.sendMessage(Text.literal("No puedes transferirte a ti mismo.").formatted(Formatting.RED));
            return;
        }
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(player.getUuid(), currencyId, amount)) {
            player.sendMessage(Text.literal("Saldo insuficiente.").formatted(Formatting.RED));
            return;
        }
        long tax = transferTax(server, currencyId, amount);
        bm.add(targetId, currencyId, amount - tax);
        Currency c = CurrencyManager.get(server).getById(currencyId);
        String name = c != null ? c.getName() : currencyId;
        player.sendMessage(Text.literal("Transferidas " + amount + " " + name + " a " + target
                + (tax > 0 ? " (impuesto " + tax + ")" : "") + ".").formatted(Formatting.GREEN));
        Jobs.payWage(player, Job.BANKING);
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetId);
        if (targetPlayer != null) {
            targetPlayer.sendMessage(Text.literal("Has recibido " + (amount - tax) + " " + name + " de "
                    + player.getNameForScoreboard() + ".").formatted(Formatting.GREEN));
        }
    }

    /** Impuesto de transferencia (parte 4): % del país que emite la moneda. Mueve el corte a su cuenta. */
    public static long transferTax(MinecraftServer server, String currencyId, long amount) {
        var g = com.realeconomy.group.GroupManager.get(server).getById(currencyId);
        if (g == null) {
            return 0L;
        }
        int pct = com.realeconomy.finance.FinanceManager.get(server).getPolicy(g.getId()).transferPercent;
        return com.realeconomy.finance.FinanceManager.get(server)
                .applyTransactionTax(server, g, pct, amount, currencyId);
    }

    // ---- Inventario ----

    private static int countCoins(ServerPlayerEntity player, String currencyId) {
        PlayerInventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (currencyId.equals(ModItems.currencyOf(stack))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int removeCoins(ServerPlayerEntity player, String currencyId, int max) {
        PlayerInventory inv = player.getInventory();
        int remaining = max;
        for (int i = 0; i < inv.size() && remaining > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (currencyId.equals(ModItems.currencyOf(stack))) {
                int take = Math.min(remaining, stack.getCount());
                stack.decrement(take);
                remaining -= take;
            }
        }
        return max - remaining;
    }

    private static void giveCoins(ServerPlayerEntity player, String currencyId, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int n = Math.min(64, remaining);
            player.giveItemStack(ModItems.createCoin(currencyId, n));
            remaining -= n;
        }
    }
}
