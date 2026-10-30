package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.market.Market;
import com.realeconomy.market.MarketEntities;
import com.realeconomy.market.MarketManager;
import com.realeconomy.market.MarketOffer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /mercado (alias /market): el líder crea y gestiona el mercado del grupo,
 * que compra/vende items por monedas físicas desde su caja.
 */
public final class MarketCommands {

    private MarketCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("mercado")
                .then(literal("crear").executes(MarketCommands::create))
                .then(literal("vender")
                        .then(argument("precio", IntegerArgumentType.integer(1))
                                .then(argument("moneda", StringArgumentType.word())
                                        .executes(ctx -> addOffer(ctx, MarketOffer.Type.SELL)))))
                .then(literal("comprar")
                        .then(argument("precio", IntegerArgumentType.integer(1))
                                .then(argument("moneda", StringArgumentType.word())
                                        .executes(ctx -> addOffer(ctx, MarketOffer.Type.BUY)))))
                .then(literal("retirar")
                        .then(argument("numero", IntegerArgumentType.integer(1))
                                .executes(MarketCommands::removeOffer)))
                .then(literal("info").executes(MarketCommands::info))
                .then(literal("borrar").executes(MarketCommands::delete));

        var node = dispatcher.register(root);
        dispatcher.register(literal("market").redirect(node));
    }

    private static Group leaderGroup(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún grupo."));
            return null;
        }
        if (!g.isLeader(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el líder puede gestionar el mercado del grupo."));
            return null;
        }
        return g;
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        MarketManager mgr = MarketManager.get(server);
        int max = ModConfig.get().maxMarketsPerGroup;
        if (mgr.countByGroup(g.getId()) >= max) {
            ctx.getSource().sendError(Text.literal("Tu grupo ya tiene el máximo de mercados (" + max + ")."));
            return 0;
        }
        Market market = mgr.createMarket(g.getId(), g.getName(), g.getTag());
        MarketEntities.spawn(player, market);
        ctx.getSource().sendFeedback(() -> Text.literal("Mercado del grupo creado. Clic derecho (miembros) para abrir la caja; "
                + "usa /mercado vender y /mercado comprar para crear ofertas.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static Market requireNearOwnMarket(CommandContext<ServerCommandSource> ctx, Group g, ServerPlayerEntity player) {
        MarketManager mgr = MarketManager.get(ctx.getSource().getServer());
        String id = MarketEntities.nearestOfGroup(player, mgr, g.getId());
        if (id == null) {
            ctx.getSource().sendError(Text.literal("Acércate a un mercado de tu grupo."));
            return null;
        }
        return mgr.getById(id);
    }

    private static Currency resolveCurrency(CommandContext<ServerCommandSource> ctx, String tag) {
        MinecraftServer server = ctx.getSource().getServer();
        Group g = GroupManager.get(server).getByTag(tag);
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No existe un grupo con esas siglas."));
            return null;
        }
        Currency c = CurrencyManager.get(server).getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Ese grupo no tiene moneda."));
            return null;
        }
        return c;
    }

    private static int addOffer(CommandContext<ServerCommandSource> ctx, MarketOffer.Type type) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Market market = requireNearOwnMarket(ctx, g, player);
        if (market == null) {
            return 0;
        }
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Sostén en la mano el item de la oferta."));
            return 0;
        }
        Currency c = resolveCurrency(ctx, StringArgumentType.getString(ctx, "moneda"));
        if (c == null) {
            return 0;
        }
        int price = IntegerArgumentType.getInteger(ctx, "precio");
        market.getOffers().add(new MarketOffer(type, hand.copyWithCount(1), price, c.getId()));
        MarketManager.get(ctx.getSource().getServer()).markDirty();
        String verbo = type == MarketOffer.Type.SELL ? "venta" : "compra";
        String nota = type == MarketOffer.Type.SELL
                ? "Mete stock del item en la caja."
                : "Mete monedas en la caja para poder pagar.";
        ctx.getSource().sendFeedback(() -> Text.literal("Oferta de " + verbo + " creada: " + hand.getName().getString()
                + " a " + price + " " + c.getName() + ". " + nota).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int removeOffer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Market market = requireNearOwnMarket(ctx, g, player);
        if (market == null) {
            return 0;
        }
        int n = IntegerArgumentType.getInteger(ctx, "numero");
        List<MarketOffer> offers = market.getOffers();
        if (n > offers.size()) {
            ctx.getSource().sendError(Text.literal("No existe esa oferta. Usa /mercado info."));
            return 0;
        }
        offers.remove(n - 1);
        MarketManager.get(ctx.getSource().getServer()).markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Oferta " + n + " retirada."), false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Market market = requireNearOwnMarket(ctx, g, player);
        if (market == null) {
            return 0;
        }
        CurrencyManager cm = CurrencyManager.get(ctx.getSource().getServer());
        List<MarketOffer> offers = market.getOffers();
        ctx.getSource().sendFeedback(() -> {
            net.minecraft.text.MutableText t = Text.literal("=== Mercado de tu grupo (" + offers.size() + " ofertas) ===");
            for (int i = 0; i < offers.size(); i++) {
                MarketOffer o = offers.get(i);
                Currency c = cm.getById(o.getCurrencyId());
                String money = c != null ? c.getName() : o.getCurrencyId();
                String kind = o.getType() == MarketOffer.Type.SELL ? "VENDE" : "COMPRA";
                t.append(Text.literal("\n" + (i + 1) + ". " + kind + " " + o.getTemplate().getName().getString()
                        + " a " + o.getPrice() + " " + money));
            }
            return t;
        }, false);
        return 1;
    }

    private static int delete(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MarketManager mgr = MarketManager.get(ctx.getSource().getServer());
        String id = MarketEntities.nearestOfGroup(player, mgr, g.getId());
        if (id == null) {
            ctx.getSource().sendError(Text.literal("Acércate a un mercado de tu grupo."));
            return 0;
        }
        MarketEntities.removeNpc(player, id);
        List<ItemStack> contents = mgr.removeMarket(id);
        for (ItemStack stack : contents) {
            player.giveItemStack(stack);
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Mercado eliminado. Se te devolvió el contenido de la caja."), false);
        return 1;
    }
}
