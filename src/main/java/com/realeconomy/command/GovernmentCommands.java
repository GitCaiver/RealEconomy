package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.economy.MonetaryManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.util.TimeUtil;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /gobierno (alias /gov): política monetaria del país. El líder imprime
 * o quema dinero; cualquier miembro consulta la circulación, el valor y el historial.
 */
public final class GovernmentCommands {

    private GovernmentCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("gobierno")
                .then(literal("imprimir")
                        .then(argument("cantidad", IntegerArgumentType.integer(1))
                                .executes(GovernmentCommands::print)))
                .then(literal("quemar")
                        .then(argument("cantidad", IntegerArgumentType.integer(1))
                                .executes(GovernmentCommands::burn)))
                .then(literal("stats").executes(GovernmentCommands::stats))
                .then(literal("historial").executes(GovernmentCommands::history)));
        dispatcher.register(literal("gov").redirect(node));
    }

    // ---- Helpers ----

    private static Group memberCountry(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(p.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return null;
        }
        return g;
    }

    private static Group leaderCountry(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = memberCountry(ctx);
        if (g == null) {
            return null;
        }
        if (!g.isLeader(ctx.getSource().getPlayerOrThrow().getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el líder del país puede dirigir la política monetaria."));
            return null;
        }
        return g;
    }

    private static Currency currencyOf(CommandContext<ServerCommandSource> ctx, Group g) {
        Currency c = CurrencyManager.get(ctx.getSource().getServer()).getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Tu país no tiene moneda."));
        }
        return c;
    }

    private static MutableText tag(Group g) {
        return Text.literal("[" + g.getTag() + "]").formatted(g.getColor());
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    // ---- Comandos ----

    private static int print(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderCountry(ctx);
        if (g == null) {
            return 0;
        }
        Currency c = currencyOf(ctx, g);
        if (c == null) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "cantidad");
        var server = ctx.getSource().getServer();
        BalanceManager.get(server).add(FinanceManager.accountUuid(g), c.getId(), amount);
        MonetaryManager mm = MonetaryManager.get(server);
        mm.print(c.getId(), amount, ctx.getSource().getName());
        ctx.getSource().sendFeedback(() -> Text.literal("Impresas " + amount + " " + c.getName() + " para ")
                .append(tag(g)).append(Text.literal(". Circulación: " + mm.circulation(c.getId())
                        + " · valor: " + fmt(mm.value(c.getId())) + " esmeraldas/moneda."))
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int burn(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderCountry(ctx);
        if (g == null) {
            return 0;
        }
        Currency c = currencyOf(ctx, g);
        if (c == null) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "cantidad");
        var server = ctx.getSource().getServer();
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(FinanceManager.accountUuid(g), c.getId(), amount)) {
            ctx.getSource().sendError(Text.literal("La cuenta del país no tiene saldo suficiente para quemar "
                    + amount + " " + c.getName() + "."));
            return 0;
        }
        MonetaryManager mm = MonetaryManager.get(server);
        mm.burn(c.getId(), amount, ctx.getSource().getName());
        ctx.getSource().sendFeedback(() -> Text.literal("Retiradas " + amount + " " + c.getName() + " de ")
                .append(tag(g)).append(Text.literal(". Circulación: " + mm.circulation(c.getId())
                        + " · valor: " + fmt(mm.value(c.getId())) + " esmeraldas/moneda."))
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int stats(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = memberCountry(ctx);
        if (g == null) {
            return 0;
        }
        Currency c = currencyOf(ctx, g);
        if (c == null) {
            return 0;
        }
        var server = ctx.getSource().getServer();
        MonetaryManager mm = MonetaryManager.get(server);
        long circ = mm.circulation(c.getId());
        double value = mm.value(c.getId());
        long account = BalanceManager.get(server).get(FinanceManager.accountUuid(g), c.getId());
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Política monetaria de ").append(tag(g))
                    .append(Text.literal(" (" + c.getName() + "):"));
            t.append(Text.literal("\n - En circulación: " + circ));
            t.append(Text.literal("\n - Valor actual: " + fmt(value) + " esmeraldas/moneda"));
            t.append(Text.literal("\n - Equivalencia: 1 esmeralda ≈ "
                    + (value > 0 ? fmt(1.0 / value) : "∞") + " " + c.getName()));
            t.append(Text.literal("\n - En la cuenta del país: " + account));
            return t;
        }, false);
        return 1;
    }

    private static int history(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = memberCountry(ctx);
        if (g == null) {
            return 0;
        }
        Currency c = currencyOf(ctx, g);
        if (c == null) {
            return 0;
        }
        List<MonetaryManager.MoneyLog> logs = MonetaryManager.get(ctx.getSource().getServer()).logs(c.getId());
        long now = System.currentTimeMillis();
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Historial monetario de ").append(tag(g)).append(Text.literal(":"));
            if (logs.isEmpty()) {
                t.append(Text.literal("\n (sin emisiones todavía)"));
                return t;
            }
            int from = Math.max(0, logs.size() - 10);
            for (int i = logs.size() - 1; i >= from; i--) {
                MonetaryManager.MoneyLog log = logs.get(i);
                String sign = log.print ? "+" : "-";
                Formatting col = log.print ? Formatting.GREEN : Formatting.YELLOW;
                t.append(Text.literal("\n " + sign + log.amount).formatted(col))
                        .append(Text.literal(" (" + (log.print ? "impreso" : "quemado") + ") por "
                                + log.actor + " · hace " + TimeUtil.format(now - log.time)
                                + " · circ. " + log.after + " · valor "
                                + fmt(MonetaryManager.valueForCirculation(log.after))));
            }
            return t;
        }, false);
        return 1;
    }
}
