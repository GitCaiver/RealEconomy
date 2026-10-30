package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.buyer.BuyerActions;
import com.realeconomy.buyer.BuyerCategories;
import com.realeconomy.buyer.BuyerEntities;
import com.realeconomy.buyer.BuyerMarketManager;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /comprador (alias /buyer): el líder crea NPC compradores de su país;
 * cualquiera consulta precios y vende items por categoría. El pago sale de la cuenta del país.
 */
public final class BuyerCommands {

    private BuyerCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("comprador")
                .then(literal("crear").executes(BuyerCommands::create))
                .then(literal("borrar").executes(BuyerCommands::delete))
                .then(literal("precios").executes(BuyerCommands::prices))
                .then(literal("vender")
                        .executes(ctx -> sell(ctx, Integer.MAX_VALUE))
                        .then(argument("cantidad", IntegerArgumentType.integer(1))
                                .executes(ctx -> sell(ctx, IntegerArgumentType.getInteger(ctx, "cantidad"))))));
        dispatcher.register(literal("buyer").redirect(node));
    }

    private static Group leaderCountry(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(p.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return null;
        }
        if (!g.isLeader(p.getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el líder del país puede gestionar los compradores."));
            return null;
        }
        return g;
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        if (CurrencyManager.get(server).getByGroup(g.getId()) == null) {
            ctx.getSource().sendError(Text.literal("Tu país no tiene moneda."));
            return 0;
        }
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        BuyerEntities.spawn(player, g);
        ctx.getSource().sendFeedback(() -> Text.literal("Comprador de " + g.getName()
                + " creado. Clic derecho para vender por categorías; paga desde la cuenta del país.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int delete(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderCountry(ctx);
        if (g == null) {
            return 0;
        }
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        VillagerEntity npc = BuyerEntities.nearest(player);
        if (npc == null || !g.getId().equals(BuyerEntities.countryIdOf(npc))) {
            ctx.getSource().sendError(Text.literal("Acércate a un comprador de tu país."));
            return 0;
        }
        npc.discard();
        ctx.getSource().sendFeedback(() -> Text.literal("Comprador eliminado.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int prices(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        // Usa el país del comprador cercano; si no hay, el propio país del jugador.
        VillagerEntity npc = BuyerEntities.nearest(player);
        Group g = npc != null ? BuyerActions.countryOf(server, npc) : GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("Acércate a un comprador o pertenece a un país."));
            return 0;
        }
        Currency c = CurrencyManager.get(server).getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Ese país no tiene moneda."));
            return 0;
        }
        BuyerMarketManager mgr = BuyerMarketManager.get(server);
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Precios del comprador de [" + g.getTag() + "] (" + c.getName() + "):");
            for (String cat : ModConfig.get().buyerCategories.keySet()) {
                long coins = BuyerMarketManager.toCoins(server, c.getId(), mgr.categoryEmeraldPrice(cat));
                int pct = (int) Math.round(mgr.multiplier(cat) * 100);
                t.append(Text.literal("\n - " + cat + ": " + coins + " " + c.getName() + "/ud (" + pct + "%)"));
            }
            if (!ModConfig.get().buyersDynamicPricing) {
                t.append(Text.literal("\n (precios fijos: oferta/demanda desactivada)").formatted(Formatting.GRAY));
            }
            return t;
        }, false);
        return 1;
    }

    private static int sell(CommandContext<ServerCommandSource> ctx, int amount) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        VillagerEntity npc = BuyerEntities.nearest(player);
        if (npc == null) {
            ctx.getSource().sendError(Text.literal("No hay ningún comprador cerca."));
            return 0;
        }
        Group g = BuyerActions.countryOf(server, npc);
        Currency c = g == null ? null : CurrencyManager.get(server).getByGroup(g.getId());
        if (g == null || c == null) {
            ctx.getSource().sendError(Text.literal("Este comprador no está operativo."));
            return 0;
        }
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Sostén en la mano el item que quieres vender."));
            return 0;
        }
        if (BuyerCategories.categoryOf(hand.getItem()) == null) {
            ctx.getSource().sendError(Text.literal("Ese item no pertenece a ninguna categoría de compra."));
            return 0;
        }
        long[] r = BuyerActions.sellItemType(server, g, c.getId(), player, hand.getItem(), amount);
        if (r[0] <= 0) {
            ctx.getSource().sendError(Text.literal("No se pudo vender (¿el país no tiene fondos?)."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Vendidas " + r[0] + " unidades por " + r[1] + " " + c.getName() + ".")
                .formatted(Formatting.GREEN), false);
        return 1;
    }
}
