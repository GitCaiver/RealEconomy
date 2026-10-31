package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.finance.Tax;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.util.TimeUtil;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Comandos /impuesto (alias /tax): el líder gestiona los impuestos del grupo. */
public final class TaxCommands {

    private static final long MIN_PERIOD = 5000L; // 5 s mínimo

    private TaxCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("impuesto")
                .then(literal("crear")
                        .then(argument("nombre", StringArgumentType.word())
                                .then(literal("fijo")
                                        .then(argument("cantidad", LongArgumentType.longArg(1))
                                                .then(argument("periodo", StringArgumentType.word())
                                                        .executes(ctx -> createFixed(ctx)))))
                                .then(literal("porcentaje")
                                        .then(argument("porcentaje", IntegerArgumentType.integer(1, 100))
                                                .then(argument("periodo", StringArgumentType.word())
                                                        .executes(ctx -> createPercent(ctx)))))))
                .then(literal("lista").executes(TaxCommands::list))
                .then(literal("tipos").executes(TaxCommands::listTypes))
                .then(literal("tipo")
                        .then(argument("tipo", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(
                                        java.util.List.of("renta", "ventatienda", "cuotatienda", "mercado",
                                                "transferencia", "cambio", "subasta"), b))
                                .then(argument("valor", IntegerArgumentType.integer(-1, 1_000_000))
                                        .executes(ctx -> setType(ctx, null))
                                        .then(argument("periodo", StringArgumentType.word())
                                                .executes(ctx -> setType(ctx, StringArgumentType.getString(ctx, "periodo")))))))
                .then(literal("borrar")
                        .then(argument("nombre", StringArgumentType.word())
                                .executes(TaxCommands::remove)))
                .then(literal("cobrar")
                        .then(argument("nombre", StringArgumentType.word())
                                .executes(TaxCommands::collectNow)));

        var node = dispatcher.register(root);
        dispatcher.register(literal("tax").redirect(node));
    }

    /** Grupo del jugador (cualquiera). Envía error si no tiene grupo. */
    private static Group playerGroup(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún grupo."));
        }
        return g;
    }

    /** Grupo del jugador exigiendo que sea el líder. */
    private static Group leaderGroup(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return null;
        }
        if (!g.isGovernment(ctx.getSource().getPlayerOrThrow().getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el gobierno (líder o ministros) puede gestionar los impuestos."));
            return null;
        }
        return g;
    }

    private static boolean ensureCurrency(CommandContext<ServerCommandSource> ctx, Group g) {
        if (CurrencyManager.get(ctx.getSource().getServer()).getByGroup(g.getId()) == null) {
            ctx.getSource().sendError(Text.literal("Tu grupo necesita una moneda. Créala con /moneda crear <nombre>."));
            return false;
        }
        return true;
    }

    private static long parsePeriodOrError(CommandContext<ServerCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "periodo");
        long period = TimeUtil.parsePeriod(raw);
        if (period < 0) {
            ctx.getSource().sendError(Text.literal("Periodo no válido. Usa p. ej. 30s, 10m, 2h o 1d."));
            return -1;
        }
        if (period < MIN_PERIOD) {
            ctx.getSource().sendError(Text.literal("El periodo mínimo es 5s."));
            return -1;
        }
        return period;
    }

    private static int createFixed(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null || !ensureCurrency(ctx, g)) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        FinanceManager fm = FinanceManager.get(ctx.getSource().getServer());
        if (fm.getTax(g.getId(), name) != null) {
            ctx.getSource().sendError(Text.literal("Ya existe un impuesto con ese nombre."));
            return 0;
        }
        long period = parsePeriodOrError(ctx);
        if (period < 0) {
            return 0;
        }
        long amount = LongArgumentType.getLong(ctx, "cantidad");
        fm.addTax(g.getId(), Tax.fixed(name, amount, period, System.currentTimeMillis()));
        ctx.getSource().sendFeedback(() -> Text.literal("Impuesto \"" + name + "\" creado: " + amount
                + " fijo cada " + TimeUtil.format(period) + "."), true);
        return 1;
    }

    private static int createPercent(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null || !ensureCurrency(ctx, g)) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        FinanceManager fm = FinanceManager.get(ctx.getSource().getServer());
        if (fm.getTax(g.getId(), name) != null) {
            ctx.getSource().sendError(Text.literal("Ya existe un impuesto con ese nombre."));
            return 0;
        }
        long period = parsePeriodOrError(ctx);
        if (period < 0) {
            return 0;
        }
        int percent = IntegerArgumentType.getInteger(ctx, "porcentaje");
        fm.addTax(g.getId(), Tax.percent(name, percent, period, System.currentTimeMillis()));
        ctx.getSource().sendFeedback(() -> Text.literal("Impuesto \"" + name + "\" creado: " + percent
                + "% cada " + TimeUtil.format(period) + "."), true);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        var taxes = FinanceManager.get(ctx.getSource().getServer()).taxes(g.getId());
        if (taxes.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Tu grupo no tiene impuestos."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Impuestos de ")
                    .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor())).append(":");
            long now = System.currentTimeMillis();
            for (Tax tax : taxes) {
                long next = Math.max(0, tax.getPeriodMillis() - (now - tax.getLastRun()));
                t.append(Text.literal("\n - " + tax.getName() + ": " + tax.amountLabel()
                        + " cada " + TimeUtil.format(tax.getPeriodMillis())
                        + " (próximo en " + TimeUtil.format(next) + ")"));
            }
            return t;
        }, false);
        return 1;
    }

    /** Lista los tipos de impuesto configurables del país y su valor actual. */
    private static int listTypes(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        FinanceManager fm = FinanceManager.get(ctx.getSource().getServer());
        var p = fm.getPolicy(g.getId());
        int rent = fm.getTaxRate(g.getId());
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Impuestos por tipo de ")
                    .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor())).append(":");
            t.append(Text.literal("\n - renta (miembros): " + rent + "%  [cada " + com.realeconomy.util.TimeUtil.format(
                    com.realeconomy.util.TimeUtil.parsePeriod(com.realeconomy.config.ModConfig.get().countryTaxPeriod)) + "]"));
            t.append(Text.literal("\n - ventatienda: " + p.shopSalePercent + "%"));
            t.append(Text.literal("\n - cuotatienda: " + p.shopFeeAmount + " cada " + p.shopFeePeriod + "/tienda"));
            t.append(Text.literal("\n - mercado (venta a NPC): " + p.marketSalePercent + "%"));
            t.append(Text.literal("\n - transferencia: " + p.transferPercent + "%"));
            t.append(Text.literal("\n - cambio: " + fm.effectiveExchangePercent(g.getId()) + "%"
                    + (p.exchangePercent < 0 ? " (por defecto)" : "")));
            t.append(Text.literal("\n - subasta: " + fm.effectiveAuctionPercent(g.getId()) + "%"
                    + (p.auctionPercent < 0 ? " (por defecto)" : "")));
            t.append(Text.literal("\nEditar: /impuesto tipo <tipo> <valor> [periodo]").formatted(
                    net.minecraft.util.Formatting.GRAY));
            return t;
        }, false);
        return 1;
    }

    /** Ajusta un tipo de impuesto. valor = % (o cantidad en cuotatienda; -1 = por defecto en cambio/subasta). */
    private static int setType(CommandContext<ServerCommandSource> ctx, String period) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        String type = StringArgumentType.getString(ctx, "tipo").toLowerCase(java.util.Locale.ROOT);
        int value = IntegerArgumentType.getInteger(ctx, "valor");
        FinanceManager fm = FinanceManager.get(ctx.getSource().getServer());
        var p = fm.editPolicy(g.getId());
        String msg;
        switch (type) {
            case "renta" -> {
                fm.setTaxRate(g.getId(), clamp(value));
                msg = "Impuesto de renta ajustado al " + clamp(value) + "%.";
            }
            case "ventatienda" -> {
                p.shopSalePercent = clamp(value);
                msg = "Impuesto de venta de tienda: " + p.shopSalePercent + "%.";
            }
            case "mercado" -> {
                p.marketSalePercent = clamp(value);
                msg = "Impuesto de venta a NPC de mercado: " + p.marketSalePercent + "%.";
            }
            case "transferencia" -> {
                p.transferPercent = clamp(value);
                msg = "Impuesto de transferencia: " + p.transferPercent + "%.";
            }
            case "cambio" -> {
                p.exchangePercent = value < 0 ? -1 : clamp(value);
                msg = "Comisión de cambio: " + (p.exchangePercent < 0 ? "por defecto" : p.exchangePercent + "%") + ".";
            }
            case "subasta" -> {
                p.auctionPercent = value < 0 ? -1 : clamp(value);
                msg = "Comisión de subasta: " + (p.auctionPercent < 0 ? "por defecto" : p.auctionPercent + "%") + ".";
            }
            case "cuotatienda" -> {
                p.shopFeeAmount = Math.max(0, value);
                if (period != null) {
                    long parsed = TimeUtil.parsePeriod(period);
                    if (parsed < MIN_PERIOD) {
                        ctx.getSource().sendError(Text.literal("Periodo no válido (mínimo 5s)."));
                        return 0;
                    }
                    p.shopFeePeriod = period;
                }
                p.shopFeeLast = System.currentTimeMillis();
                msg = "Cuota de tienda: " + p.shopFeeAmount + " cada " + p.shopFeePeriod + " por tienda.";
            }
            default -> {
                ctx.getSource().sendError(Text.literal("Tipo no válido. Usa: renta, ventatienda, cuotatienda, "
                        + "mercado, transferencia, cambio, subasta."));
                return 0;
            }
        }
        fm.savePolicy();
        String fmsg = msg;
        ctx.getSource().sendFeedback(() -> Text.literal(fmsg), true);
        return 1;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static int remove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        if (!FinanceManager.get(ctx.getSource().getServer()).removeTax(g.getId(), name)) {
            ctx.getSource().sendError(Text.literal("No existe un impuesto con ese nombre."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Impuesto \"" + name + "\" eliminado."), true);
        return 1;
    }

    private static int collectNow(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        Tax tax = FinanceManager.get(ctx.getSource().getServer()).getTax(g.getId(), name);
        if (tax == null) {
            ctx.getSource().sendError(Text.literal("No existe un impuesto con ese nombre."));
            return 0;
        }
        // Fuerza el cobro en el próximo tick poniendo el último cobro "en el pasado".
        tax.setLastRun(0L);
        FinanceManager.get(ctx.getSource().getServer()).markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("El impuesto \"" + name + "\" se cobrará ahora."), true);
        return 1;
    }
}
