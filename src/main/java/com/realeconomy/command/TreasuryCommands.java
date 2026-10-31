package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.finance.Salary;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.util.TimeUtil;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /tesoreria (alias /cuenta): ver la cuenta del grupo, pagar subvenciones
 * y gestionar sueldos recurrentes, todo desde la cuenta del grupo.
 */
public final class TreasuryCommands {

    private static final long MIN_PERIOD = 5000L;

    private TreasuryCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("tesoreria")
                .executes(TreasuryCommands::view)
                .then(literal("ver").executes(TreasuryCommands::view))
                .then(literal("pagar")
                        .then(argument("jugador", StringArgumentType.word())
                                .then(argument("cantidad", LongArgumentType.longArg(1))
                                        .executes(TreasuryCommands::pay))))
                .then(literal("sueldos").executes(TreasuryCommands::salaryList))
                .then(literal("sueldoset")
                        .then(argument("jugador", StringArgumentType.word())
                                .then(argument("cantidad", LongArgumentType.longArg(1))
                                        .then(argument("periodo", StringArgumentType.word())
                                                .executes(TreasuryCommands::salarySet)))))
                .then(literal("sueldoquitar")
                        .then(argument("jugador", StringArgumentType.word())
                                .executes(TreasuryCommands::salaryRemove)));

        var node = dispatcher.register(root);
        dispatcher.register(literal("cuenta").redirect(node));
    }

    private static Group playerGroup(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún grupo."));
        }
        return g;
    }

    private static Group leaderGroup(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return null;
        }
        if (!g.isGovernment(ctx.getSource().getPlayerOrThrow().getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el gobierno (líder o ministros) puede gestionar la tesorería."));
            return null;
        }
        return g;
    }

    private static Currency currencyOf(CommandContext<ServerCommandSource> ctx, Group g) {
        Currency c = CurrencyManager.get(ctx.getSource().getServer()).getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Tu grupo necesita una moneda. Créala con /moneda crear <nombre>."));
        }
        return c;
    }

    private static int view(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        BalanceManager bm = BalanceManager.get(ctx.getSource().getServer());
        CurrencyManager cm = CurrencyManager.get(ctx.getSource().getServer());
        Map<String, Long> all = bm.getAll(FinanceManager.accountUuid(g));
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Cuenta de ")
                    .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor()))
                    .append(Text.literal(" (" + g.getName() + "):"));
            if (all.isEmpty()) {
                t.append(Text.literal("\n - (vacía)"));
            } else {
                for (Map.Entry<String, Long> e : all.entrySet()) {
                    Currency c = cm.getById(e.getKey());
                    String label = c != null ? c.getName() : e.getKey();
                    t.append(Text.literal("\n - " + label + ": " + e.getValue()));
                }
            }
            return t;
        }, false);
        return 1;
    }

    private static UUID resolveMember(CommandContext<ServerCommandSource> ctx, Group g) {
        String name = StringArgumentType.getString(ctx, "jugador");
        UUID uuid = g.getMemberByName(name);
        if (uuid == null) {
            ctx.getSource().sendError(Text.literal("\"" + name + "\" no es miembro de tu grupo."));
        }
        return uuid;
    }

    private static int pay(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        Currency c = currencyOf(ctx, g);
        if (c == null) {
            return 0;
        }
        UUID target = resolveMember(ctx, g);
        if (target == null) {
            return 0;
        }
        long amount = LongArgumentType.getLong(ctx, "cantidad");
        BalanceManager bm = BalanceManager.get(ctx.getSource().getServer());
        if (!bm.transfer(FinanceManager.accountUuid(g), target, c.getId(), amount)) {
            ctx.getSource().sendError(Text.literal("La cuenta del grupo no tiene saldo suficiente."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "jugador");
        ctx.getSource().sendFeedback(() -> Text.literal("Pagados " + amount + " " + c.getName()
                + " a " + name + " desde la cuenta del grupo."), true);
        ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(target);
        if (online != null) {
            online.sendMessage(Text.literal("Has recibido una subvención de " + amount + " " + c.getName() + "."), false);
        }
        return 1;
    }

    private static int salaryList(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        var salaries = FinanceManager.get(ctx.getSource().getServer()).salaries(g.getId());
        if (salaries.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Tu grupo no tiene sueldos configurados."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Sueldos de ")
                    .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor())).append(":");
            for (Salary s : salaries.values()) {
                t.append(Text.literal("\n - " + s.getMemberName() + ": " + s.getAmount()
                        + " cada " + TimeUtil.format(s.getPeriodMillis())));
            }
            return t;
        }, false);
        return 1;
    }

    private static int salarySet(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null || currencyOf(ctx, g) == null) {
            return 0;
        }
        UUID target = resolveMember(ctx, g);
        if (target == null) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "periodo");
        long period = TimeUtil.parsePeriod(raw);
        if (period < 0) {
            ctx.getSource().sendError(Text.literal("Periodo no válido. Usa p. ej. 30s, 10m, 2h o 1d."));
            return 0;
        }
        if (period < MIN_PERIOD) {
            ctx.getSource().sendError(Text.literal("El periodo mínimo es 5s."));
            return 0;
        }
        long amount = LongArgumentType.getLong(ctx, "cantidad");
        String name = StringArgumentType.getString(ctx, "jugador");
        FinanceManager.get(ctx.getSource().getServer()).setSalary(g.getId(),
                new Salary(target, name, amount, period, System.currentTimeMillis()));
        ctx.getSource().sendFeedback(() -> Text.literal("Sueldo de " + name + " fijado en " + amount
                + " cada " + TimeUtil.format(period) + "."), true);
        return 1;
    }

    private static int salaryRemove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        UUID target = resolveMember(ctx, g);
        if (target == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "jugador");
        if (!FinanceManager.get(ctx.getSource().getServer()).removeSalary(g.getId(), target)) {
            ctx.getSource().sendError(Text.literal(name + " no tiene sueldo configurado."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Sueldo de " + name + " eliminado."), true);
        return 1;
    }
}
