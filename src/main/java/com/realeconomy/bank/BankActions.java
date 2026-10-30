package com.realeconomy.bank;

import com.mojang.authlib.GameProfile;
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

    /** Abre la GUI del banco para el jugador (envía sus saldos). */
    public static void openFor(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new OpenBankPayload(snapshot(player)));
    }

    private static Map<String, Long> snapshot(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        BalanceManager bm = BalanceManager.get(server);
        return new LinkedHashMap<>(bm.getAll(player.getUuid()));
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
            default -> {
            }
        }
        openFor(player); // refresca la GUI con los saldos actualizados
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
        if (!bm.transfer(player.getUuid(), targetId, currencyId, amount)) {
            player.sendMessage(Text.literal("Saldo insuficiente.").formatted(Formatting.RED));
            return;
        }
        Currency c = CurrencyManager.get(server).getById(currencyId);
        String name = c != null ? c.getName() : currencyId;
        player.sendMessage(Text.literal("Transferidas " + amount + " " + name + " a " + target + ".").formatted(Formatting.GREEN));
        Jobs.payWage(player, Job.BANKING);
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetId);
        if (targetPlayer != null) {
            targetPlayer.sendMessage(Text.literal("Has recibido " + amount + " " + name + " de "
                    + player.getNameForScoreboard() + ".").formatted(Formatting.GREEN));
        }
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
