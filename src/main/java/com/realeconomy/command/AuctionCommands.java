package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.auction.Auction;
import com.realeconomy.auction.AuctionManager;
import com.realeconomy.auction.AuctionService;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.townhall.LicenseManager;
import com.realeconomy.townhall.TownhallEntities;
import com.realeconomy.util.TimeUtil;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos de subastas: /subasta (jugadores: licencia/crear/pujar/cancelar/info/lista/recoger)
 * y /subastacrear /subastaborrar (op: pedestales).
 */
public final class AuctionCommands {

    private AuctionCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("subasta")
                .then(literal("licencia").executes(AuctionCommands::license))
                .then(literal("crear")
                        .then(argument("precio", IntegerArgumentType.integer(1))
                                .then(argument("moneda", StringArgumentType.word())
                                        .then(argument("duracion", StringArgumentType.word())
                                                .executes(AuctionCommands::create)))))
                .then(literal("pujar")
                        .then(argument("cantidad", IntegerArgumentType.integer(1))
                                .executes(AuctionCommands::bid)))
                .then(literal("cancelar").executes(AuctionCommands::cancel))
                .then(literal("info").executes(AuctionCommands::info))
                .then(literal("lista").executes(AuctionCommands::list))
                .then(literal("recoger").executes(AuctionCommands::collect)));
        dispatcher.register(literal("auction").redirect(node));

        dispatcher.register(literal("subastacrear")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(AuctionCommands::createPedestal));
        dispatcher.register(literal("subastaborrar")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(AuctionCommands::removePedestal));
    }

    private static Currency resolveCurrency(CommandContext<ServerCommandSource> ctx, String tag) {
        MinecraftServer server = ctx.getSource().getServer();
        Group g = GroupManager.get(server).getByTag(tag);
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No existe un país con esas siglas."));
            return null;
        }
        Currency c = CurrencyManager.get(server).getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Ese país no tiene moneda."));
            return null;
        }
        return c;
    }

    // ---- Jugadores ----

    private static int license(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        if (!TownhallEntities.isNear(player)) {
            ctx.getSource().sendError(Text.literal("Acércate a un ayuntamiento para comprar la licencia."));
            return 0;
        }
        String tag = ModConfig.get().auctionLicenseCurrencyTag;
        if (tag == null || tag.isBlank()) {
            ctx.getSource().sendError(Text.literal("El administrador no ha configurado la moneda de la licencia de subasta."));
            return 0;
        }
        Group g = GroupManager.get(server).getByTag(tag);
        Currency c = g == null ? null : CurrencyManager.get(server).getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("La moneda de la licencia de subasta no está configurada correctamente."));
            return 0;
        }
        int price = ModConfig.get().auctionLicensePrice;
        if (!BalanceManager.get(server).subtract(player.getUuid(), c.getId(), price)) {
            ctx.getSource().sendError(Text.literal("Saldo insuficiente. La licencia cuesta " + price + " " + c.getName() + "."));
            return 0;
        }
        LicenseManager.get(server).addAuction(player.getUuid(), 1);
        ctx.getSource().sendFeedback(() -> Text.literal("Licencia de subasta comprada. Úsala con /subasta crear junto a un pedestal libre.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Currency c = resolveCurrency(ctx, StringArgumentType.getString(ctx, "moneda"));
        if (c == null) {
            return 0;
        }
        long duration = TimeUtil.parsePeriod(StringArgumentType.getString(ctx, "duracion"));
        if (duration <= 0) {
            ctx.getSource().sendError(Text.literal("Duración no válida. Usa 30s, 10m, 2h, 1d..."));
            return 0;
        }
        long price = IntegerArgumentType.getInteger(ctx, "precio");
        AuctionService.place(ctx.getSource().getServer(), player, price, c.getId(), duration);
        return 1;
    }

    private static int bid(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        long amount = IntegerArgumentType.getInteger(ctx, "cantidad");
        AuctionService.bid(ctx.getSource().getServer(), player, amount);
        return 1;
    }

    private static int cancel(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        AuctionService.cancel(ctx.getSource().getServer(), ctx.getSource().getPlayerOrThrow());
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        AuctionManager mgr = AuctionManager.get(server);
        long ped = mgr.nearestPedestal(player.getPos(), ModConfig.get().auctionRange);
        Auction a = ped == -1L ? null : mgr.auctionAt(ped);
        if (a == null) {
            ctx.getSource().sendError(Text.literal("No hay ninguna subasta activa cerca."));
            return 0;
        }
        String cur = currencyName(server, a.getCurrencyId());
        long left = Math.max(0, a.getEndTime() - System.currentTimeMillis());
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("=== Subasta #" + a.getId() + " ===").formatted(Formatting.GOLD);
            t.append(Text.literal("\nItem: ").append(a.getItem().getName())
                    .append(Text.literal(" x" + a.getItem().getCount())));
            t.append(Text.literal("\nVendedor: " + a.getSellerName()));
            t.append(Text.literal("\nPrecio inicial: " + a.getStartPrice() + " " + cur));
            if (a.hasBids()) {
                t.append(Text.literal("\nPuja actual: " + a.getCurrentBid() + " " + cur + " (" + a.getBidderName() + ")"));
            } else {
                t.append(Text.literal("\nPuja actual: sin pujas"));
            }
            t.append(Text.literal("\nTiempo restante: " + TimeUtil.format(left)));
            if (a.getItem().getMaxDamage() > 0) {
                int dur = a.getItem().getMaxDamage() - a.getItem().getDamage();
                t.append(Text.literal("\nDurabilidad: " + dur + "/" + a.getItem().getMaxDamage()));
            }
            if (a.getItem().hasEnchantments()) {
                t.append(Text.literal("\n(con encantamientos)").formatted(Formatting.LIGHT_PURPLE));
            }
            return t;
        }, false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        AuctionManager mgr = AuctionManager.get(server);
        long now = System.currentTimeMillis();
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("=== Subastas activas (" + mgr.all().size() + ") ===");
            if (mgr.all().isEmpty()) {
                t.append(Text.literal("\n (no hay subastas ahora mismo)"));
            }
            for (Auction a : mgr.all()) {
                BlockPos p = BlockPos.fromLong(a.getPedestal());
                String bid = a.hasBids() ? (a.getCurrentBid() + " " + currencyName(server, a.getCurrencyId())) : "sin pujas";
                t.append(Text.literal("\n#" + a.getId() + " " + a.getItem().getName().getString()
                        + " · " + bid + " · " + TimeUtil.format(Math.max(0, a.getEndTime() - now))
                        + " · (" + p.getX() + "," + p.getY() + "," + p.getZ() + ")"));
            }
            return t;
        }, false);
        return 1;
    }

    private static int collect(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        int n = AuctionService.collect(ctx.getSource().getServer(), player);
        if (n <= 0) {
            ctx.getSource().sendFeedback(() -> Text.literal("No tienes nada pendiente de recoger."), false);
        } else {
            ctx.getSource().sendFeedback(() -> Text.literal("Recogidos " + n + " objeto(s) de subastas.").formatted(Formatting.GREEN), false);
        }
        return 1;
    }

    // ---- Op: pedestales ----

    private static int createPedestal(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ServerWorld world = player.getServerWorld();
        HitResult hit = player.raycast(6.0, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendError(Text.literal("Mira al bloque donde quieres el pedestal."));
            return 0;
        }
        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos pos = bhr.getBlockPos().offset(bhr.getSide());
        AuctionManager mgr = AuctionManager.get(ctx.getSource().getServer());
        if (mgr.isPedestal(pos)) {
            ctx.getSource().sendError(Text.literal("Ahí ya hay un pedestal."));
            return 0;
        }
        Block block = Registries.BLOCK.get(Identifier.of(ModConfig.get().auctionPedestalBlock));
        world.setBlockState(pos, block.getDefaultState());
        mgr.addPedestal(pos);
        ctx.getSource().sendFeedback(() -> Text.literal("Pedestal de subasta creado en ("
                + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "). Pon un item con /subasta crear.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int removePedestal(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        AuctionManager mgr = AuctionManager.get(server);
        long ped = mgr.nearestPedestal(player.getPos(), Math.max(6.0, ModConfig.get().auctionRange));
        if (ped == -1L) {
            ctx.getSource().sendError(Text.literal("Acércate a un pedestal de subasta."));
            return 0;
        }
        Auction a = mgr.auctionAt(ped);
        if (a != null) {
            AuctionService.forceRemove(server, a);
        }
        BlockPos pos = BlockPos.fromLong(ped);
        mgr.removePedestal(pos);
        ctx.getSource().sendFeedback(() -> Text.literal("Pedestal de subasta eliminado (subasta devuelta si la había).")
                .formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static String currencyName(MinecraftServer server, String id) {
        Currency c = CurrencyManager.get(server).getById(id);
        return c != null ? c.getName() : id;
    }
}
