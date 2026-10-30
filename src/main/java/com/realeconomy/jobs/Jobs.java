package com.realeconomy.jobs;

import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Lógica común de los trabajos (parte 8): paga al trabajador el sueldo del trabajo
 * desde la <b>cuenta de su grupo</b> en la moneda del grupo. Como el dinero sale de
 * la cuenta (que alimentan los impuestos de la parte 6), no se genera inflación: si
 * la cuenta no tiene fondos, el trabajo no paga.
 */
public final class Jobs {

    private Jobs() {
    }

    /** Paga a {@code player} el sueldo del trabajo {@code job}, si procede. */
    public static void payWage(ServerPlayerEntity player, Job job) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Group group = GroupManager.get(server).getByPlayer(player.getUuid());
        if (group == null) {
            return;
        }
        long wage = FinanceManager.get(server).getWage(group.getId(), job);
        if (wage <= 0) {
            return;
        }
        Currency currency = CurrencyManager.get(server).getByGroup(group.getId());
        if (currency == null) {
            return;
        }
        String currencyId = currency.getId();
        UUID account = FinanceManager.accountUuid(group);
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(account, currencyId, wage)) {
            return; // la cuenta del grupo no tiene fondos
        }
        bm.add(player.getUuid(), currencyId, wage);
        player.sendMessage(Text.literal("+" + wage + " " + currency.getName() + " (" + job.getKey() + ")")
                .formatted(Formatting.GREEN), true); // true = barra de acción (no spamea el chat)
    }
}
