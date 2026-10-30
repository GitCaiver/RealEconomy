package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.finance.Loan;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.util.TimeUtil;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /prestamo (alias /loan): el líder, como banquero del grupo, concede
 * préstamos desde la cuenta del grupo a un miembro (capital, interés y plazo en
 * tiempo real). Al vencer, lo pendiente se cobra solo del saldo del deudor; si no
 * llega, queda deuda. Cada miembro puede tener un préstamo activo a la vez.
 */
public final class LoanCommands {

    private static final long MIN_PERIOD = 5000L;

    private LoanCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("prestamo")
                .executes(LoanCommands::list)
                .then(literal("lista").executes(LoanCommands::list))
                .then(literal("mio").executes(LoanCommands::mine))
                .then(literal("conceder")
                        .then(argument("jugador", StringArgumentType.word())
                                .then(argument("cantidad", LongArgumentType.longArg(1))
                                        .then(argument("interes", IntegerArgumentType.integer(0, 1000))
                                                .then(argument("plazo", StringArgumentType.word())
                                                        .executes(LoanCommands::grant))))))
                .then(literal("pagar")
                        .executes(ctx -> repay(ctx, -1L))
                        .then(argument("cantidad", LongArgumentType.longArg(1))
                                .executes(ctx -> repay(ctx, LongArgumentType.getLong(ctx, "cantidad")))))
                .then(literal("perdonar")
                        .then(argument("jugador", StringArgumentType.word())
                                .executes(LoanCommands::forgive)));

        var node = dispatcher.register(root);
        dispatcher.register(literal("loan").redirect(node));
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
        if (!g.isLeader(ctx.getSource().getPlayerOrThrow().getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el líder puede gestionar los préstamos."));
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

    private static UUID resolveMember(CommandContext<ServerCommandSource> ctx, Group g) {
        String name = StringArgumentType.getString(ctx, "jugador");
        UUID uuid = g.getMemberByName(name);
        if (uuid == null) {
            ctx.getSource().sendError(Text.literal("\"" + name + "\" no es miembro de tu grupo."));
        }
        return uuid;
    }

    private static String loanLine(Loan l, long now) {
        if (!l.isDue(now)) {
            return l.getBorrowerName() + ": debe " + l.getOutstanding() + " (de " + l.getTotal()
                    + ", " + l.getInterestPercent() + "% interés) — vence en " + TimeUtil.format(l.getDueAt() - now);
        }
        return l.getBorrowerName() + ": debe " + l.getOutstanding() + " — VENCIDO (cobro automático)";
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        Map<UUID, Loan> loans = FinanceManager.get(ctx.getSource().getServer()).loans(g.getId());
        long now = System.currentTimeMillis();
        if (loans.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Tu grupo no tiene préstamos activos."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Préstamos de ")
                    .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor())).append(":");
            for (Loan l : loans.values()) {
                t.append(Text.literal("\n - " + loanLine(l, now)));
            }
            return t;
        }, false);
        return 1;
    }

    private static int mine(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        UUID me = ctx.getSource().getPlayerOrThrow().getUuid();
        Loan l = FinanceManager.get(ctx.getSource().getServer()).getLoan(g.getId(), me);
        long now = System.currentTimeMillis();
        if (l == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("No tienes ningún préstamo activo."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Tu préstamo: " + loanLine(l, now)
                + ". Págalo antes con /prestamo pagar [cantidad]."), false);
        return 1;
    }

    private static int grant(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
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
        FinanceManager fm = FinanceManager.get(ctx.getSource().getServer());
        if (fm.getLoan(g.getId(), target) != null) {
            ctx.getSource().sendError(Text.literal("Ese miembro ya tiene un préstamo activo. Debe saldarlo antes."));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "plazo");
        long period = TimeUtil.parsePeriod(raw);
        if (period < 0) {
            ctx.getSource().sendError(Text.literal("Plazo no válido. Usa p. ej. 30s, 10m, 2h o 1d."));
            return 0;
        }
        if (period < MIN_PERIOD) {
            ctx.getSource().sendError(Text.literal("El plazo mínimo es 5s."));
            return 0;
        }
        long principal = LongArgumentType.getLong(ctx, "cantidad");
        int interest = IntegerArgumentType.getInteger(ctx, "interes");
        BalanceManager bm = BalanceManager.get(ctx.getSource().getServer());
        if (!bm.transfer(FinanceManager.accountUuid(g), target, c.getId(), principal)) {
            ctx.getSource().sendError(Text.literal("La cuenta del grupo no tiene saldo suficiente para prestar " + principal + "."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "jugador");
        Loan loan = Loan.create(target, name, principal, interest, period, System.currentTimeMillis());
        fm.addLoan(g.getId(), loan);
        ctx.getSource().sendFeedback(() -> Text.literal("Préstamo concedido a " + name + ": " + principal
                + " " + c.getName() + " al " + interest + "%. A devolver " + loan.getTotal()
                + " en " + TimeUtil.format(period) + "."), true);
        ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(target);
        if (online != null) {
            online.sendMessage(Text.literal("Has recibido un préstamo de " + principal + " " + c.getName()
                    + ". A devolver " + loan.getTotal() + " en " + TimeUtil.format(period)
                    + " (se cobrará de tu saldo al vencer).").formatted(Formatting.YELLOW), false);
        }
        return 1;
    }

    private static int repay(CommandContext<ServerCommandSource> ctx, long requested) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        Currency c = currencyOf(ctx, g);
        if (c == null) {
            return 0;
        }
        UUID me = ctx.getSource().getPlayerOrThrow().getUuid();
        FinanceManager fm = FinanceManager.get(ctx.getSource().getServer());
        Loan loan = fm.getLoan(g.getId(), me);
        if (loan == null) {
            ctx.getSource().sendError(Text.literal("No tienes ningún préstamo activo."));
            return 0;
        }
        long amount = requested < 0 ? loan.getOutstanding() : Math.min(requested, loan.getOutstanding());
        BalanceManager bm = BalanceManager.get(ctx.getSource().getServer());
        if (!bm.transfer(me, FinanceManager.accountUuid(g), c.getId(), amount)) {
            ctx.getSource().sendError(Text.literal("No tienes saldo suficiente. Tu saldo: "
                    + bm.get(me, c.getId()) + ", pendiente: " + loan.getOutstanding() + "."));
            return 0;
        }
        loan.setOutstanding(loan.getOutstanding() - amount);
        if (loan.getOutstanding() <= 0) {
            fm.removeLoan(g.getId(), me);
            ctx.getSource().sendFeedback(() -> Text.literal("Has pagado " + amount + " " + c.getName()
                    + ". Préstamo saldado por completo."), false);
        } else {
            fm.addLoan(g.getId(), loan); // conserva el préstamo actualizado y marca el estado como sucio
            long remaining = loan.getOutstanding();
            ctx.getSource().sendFeedback(() -> Text.literal("Has pagado " + amount + " " + c.getName()
                    + ". Pendiente: " + remaining + "."), false);
        }
        return 1;
    }

    private static int forgive(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        UUID target = resolveMember(ctx, g);
        if (target == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "jugador");
        if (!FinanceManager.get(ctx.getSource().getServer()).removeLoan(g.getId(), target)) {
            ctx.getSource().sendError(Text.literal(name + " no tiene ningún préstamo activo."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Préstamo de " + name + " perdonado (deuda cancelada)."), true);
        ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(target);
        if (online != null) {
            online.sendMessage(Text.literal("El líder ha perdonado tu préstamo con el grupo.").formatted(Formatting.GREEN), false);
        }
        return 1;
    }
}
