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
import com.realeconomy.shop.Offer;
import com.realeconomy.shop.Shop;
import com.realeconomy.shop.ShopActions;
import com.realeconomy.shop.ShopEntities;
import com.realeconomy.shop.ShopManager;
import com.realeconomy.townhall.LicenseManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Comandos /tienda para que el dueño gestione su tienda. */
public final class ShopCommands {

    private ShopCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("tienda")
                .then(literal("crear").executes(ShopCommands::create))
                .then(literal("vender")
                        .then(argument("precio", IntegerArgumentType.integer(1))
                                .then(argument("moneda", StringArgumentType.word())
                                        .executes(ShopCommands::sell))))
                .then(literal("comprar")
                        .then(argument("precio", IntegerArgumentType.integer(1))
                                .then(argument("moneda", StringArgumentType.word())
                                        .then(argument("cantidad", IntegerArgumentType.integer(1))
                                                .executes(ShopCommands::buy)))))
                .then(literal("retirar")
                        .then(argument("numero", IntegerArgumentType.integer(1))
                                .executes(ShopCommands::removeOffer)))
                .then(literal("info").executes(ShopCommands::info))
                .then(literal("borrar").executes(ShopCommands::delete));

        var node = dispatcher.register(root);
        dispatcher.register(literal("shop").redirect(node));
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        ShopManager mgr = ShopManager.get(server);

        int max = ModConfig.get().maxShopsPerPlayer;
        if (mgr.countByOwner(player.getUuid()) >= max) {
            ctx.getSource().sendError(Text.literal("Has alcanzado el máximo de tiendas (" + max + ")."));
            return 0;
        }
        if (!LicenseManager.get(server).consume(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Necesitas una licencia de tienda. Cómprala en el ayuntamiento."));
            return 0;
        }
        Shop shop = mgr.createShop(player.getUuid(), player.getNameForScoreboard());
        ShopEntities.spawn(player, shop);
        ctx.getSource().sendFeedback(() -> Text.literal("Tienda creada (se consumió 1 licencia). "
                + "Clic derecho para abrir el almacén; usa /tienda vender y /tienda comprar para crear ofertas."), false);
        return 1;
    }

    private static Shop requireNearOwnShop(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
        ShopManager mgr = ShopManager.get(ctx.getSource().getServer());
        String id = ShopEntities.nearestOwned(player, mgr);
        if (id == null) {
            ctx.getSource().sendError(Text.literal("Acércate a una tienda tuya."));
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

    private static int sell(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Shop shop = requireNearOwnShop(ctx, player);
        if (shop == null) {
            return 0;
        }
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Sostén en la mano el item que quieres vender."));
            return 0;
        }
        Currency c = resolveCurrency(ctx, StringArgumentType.getString(ctx, "moneda"));
        if (c == null) {
            return 0;
        }
        int price = IntegerArgumentType.getInteger(ctx, "precio");
        shop.getOffers().add(new Offer(Offer.Type.SELL, hand.copyWithCount(1), price, c.getId(), -1));
        ShopManager.get(ctx.getSource().getServer()).markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Oferta de venta creada: " + hand.getName().getString()
                + " a " + price + " " + c.getName() + ". Mete stock en el almacén (clic derecho).").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int buy(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Shop shop = requireNearOwnShop(ctx, player);
        if (shop == null) {
            return 0;
        }
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Sostén en la mano el item que quieres comprar."));
            return 0;
        }
        Currency c = resolveCurrency(ctx, StringArgumentType.getString(ctx, "moneda"));
        if (c == null) {
            return 0;
        }
        int price = IntegerArgumentType.getInteger(ctx, "precio");
        int qty = IntegerArgumentType.getInteger(ctx, "cantidad");
        shop.getOffers().add(new Offer(Offer.Type.BUY, hand.copyWithCount(1), price, c.getId(), qty));
        ShopManager.get(ctx.getSource().getServer()).markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Oferta de compra creada: " + hand.getName().getString()
                + " a " + price + " " + c.getName() + " (máx " + qty + "). Ten saldo para pagar.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int removeOffer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Shop shop = requireNearOwnShop(ctx, player);
        if (shop == null) {
            return 0;
        }
        int n = IntegerArgumentType.getInteger(ctx, "numero");
        List<Offer> offers = shop.getOffers();
        if (n > offers.size()) {
            ctx.getSource().sendError(Text.literal("No existe esa oferta. Usa /tienda info."));
            return 0;
        }
        offers.remove(n - 1);
        ShopManager.get(ctx.getSource().getServer()).markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Oferta " + n + " retirada."), false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Shop shop = requireNearOwnShop(ctx, player);
        if (shop == null) {
            return 0;
        }
        CurrencyManager cm = CurrencyManager.get(ctx.getSource().getServer());
        List<Offer> offers = shop.getOffers();
        ctx.getSource().sendFeedback(() -> {
            net.minecraft.text.MutableText t = Text.literal("=== Tu tienda (" + offers.size() + " ofertas) ===");
            for (int i = 0; i < offers.size(); i++) {
                Offer o = offers.get(i);
                Currency c = cm.getById(o.getCurrencyId());
                String money = c != null ? c.getName() : o.getCurrencyId();
                String kind = o.getType() == Offer.Type.SELL ? "VENDE" : "COMPRA";
                String extra = o.getType() == Offer.Type.BUY ? (" (quedan " + o.getRemaining() + ")") : "";
                t.append(Text.literal("\n" + (i + 1) + ". " + kind + " " + o.getTemplate().getName().getString()
                        + " a " + o.getPrice() + " " + money + extra));
            }
            return t;
        }, false);
        return 1;
    }

    private static int delete(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ShopManager mgr = ShopManager.get(ctx.getSource().getServer());
        String id = ShopEntities.nearestOwned(player, mgr);
        if (id == null) {
            ctx.getSource().sendError(Text.literal("Acércate a una tienda tuya."));
            return 0;
        }
        ShopEntities.removeNpc(player, id);
        List<ItemStack> contents = mgr.removeShop(id);
        for (ItemStack stack : contents) {
            player.giveItemStack(stack);
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Tienda eliminada. Se te devolvió el stock del almacén."), false);
        return 1;
    }
}
