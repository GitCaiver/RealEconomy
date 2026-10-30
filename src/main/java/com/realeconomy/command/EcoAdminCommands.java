package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.item.ModItems;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Comandos de administrador (op, nivel 2): monedas físicas y saldos. */
public final class EcoAdminCommands {

    private EcoAdminCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("ecoadmin")
                .requires(src -> src.hasPermissionLevel(2))
                .then(literal("darmoneda")
                        .then(argument("jugador", EntityArgumentType.player())
                                .then(argument("siglas", StringArgumentType.word())
                                        .then(argument("cantidad", IntegerArgumentType.integer(1))
                                                .executes(EcoAdminCommands::giveCoins)))))
                .then(literal("saldo")
                        .then(literal("ver")
                                .then(argument("jugador", EntityArgumentType.player())
                                        .executes(EcoAdminCommands::balanceView)))
                        .then(literal("set")
                                .then(argument("jugador", EntityArgumentType.player())
                                        .then(argument("siglas", StringArgumentType.word())
                                                .then(argument("cantidad", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> balanceSet(ctx, true))))))
                        .then(literal("add")
                                .then(argument("jugador", EntityArgumentType.player())
                                        .then(argument("siglas", StringArgumentType.word())
                                                .then(argument("cantidad", IntegerArgumentType.integer())
                                                        .executes(ctx -> balanceSet(ctx, false)))))))
                .then(literal("reload")
                        .executes(EcoAdminCommands::reload)));
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        ModConfig.load();
        ctx.getSource().sendFeedback(() -> Text.literal("Config de RealEconomy recargada."), true);
        return 1;
    }

    private static Currency resolveCurrency(CommandContext<ServerCommandSource> ctx) {
        String tag = StringArgumentType.getString(ctx, "siglas");
        GroupManager gm = GroupManager.get(ctx.getSource().getServer());
        Group g = gm.getByTag(tag);
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No existe un grupo con esas siglas."));
            return null;
        }
        Currency c = CurrencyManager.get(ctx.getSource().getServer()).getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Ese grupo no tiene moneda."));
            return null;
        }
        return c;
    }

    private static int giveCoins(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "jugador");
        Currency c = resolveCurrency(ctx);
        if (c == null) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "cantidad");
        int remaining = amount;
        while (remaining > 0) {
            int n = Math.min(64, remaining);
            ItemStack stack = ModItems.createCoin(c.getId(), n);
            target.giveItemStack(stack);
            remaining -= n;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Entregadas " + amount + " monedas de " + c.getName()
                + " a " + target.getNameForScoreboard() + "."), true);
        return 1;
    }

    private static int balanceView(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "jugador");
        BalanceManager bm = BalanceManager.get(ctx.getSource().getServer());
        CurrencyManager cm = CurrencyManager.get(ctx.getSource().getServer());
        Map<String, Long> all = bm.getAll(target.getUuid());
        if (all.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal(target.getNameForScoreboard() + " no tiene saldo."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> {
            net.minecraft.text.MutableText t = Text.literal("Saldo de " + target.getNameForScoreboard() + ":");
            for (Map.Entry<String, Long> e : all.entrySet()) {
                Currency c = cm.getById(e.getKey());
                String label = c != null ? c.getName() : e.getKey();
                t.append(Text.literal("\n - " + label + ": " + e.getValue()));
            }
            return t;
        }, false);
        return 1;
    }

    private static int balanceSet(CommandContext<ServerCommandSource> ctx, boolean set) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "jugador");
        Currency c = resolveCurrency(ctx);
        if (c == null) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "cantidad");
        BalanceManager bm = BalanceManager.get(ctx.getSource().getServer());
        if (set) {
            long current = bm.get(target.getUuid(), c.getId());
            bm.add(target.getUuid(), c.getId(), amount - current);
            ctx.getSource().sendFeedback(() -> Text.literal("Saldo de " + target.getNameForScoreboard()
                    + " en " + c.getName() + " establecido a " + amount + "."), true);
        } else {
            bm.add(target.getUuid(), c.getId(), amount);
            ctx.getSource().sendFeedback(() -> Text.literal("Saldo de " + target.getNameForScoreboard()
                    + " en " + c.getName() + " ajustado en " + amount + " (ahora "
                    + bm.get(target.getUuid(), c.getId()) + ")."), true);
        }
        return 1;
    }
}
