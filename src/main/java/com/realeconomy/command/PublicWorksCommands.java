package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.gov.PublicWork;
import com.realeconomy.gov.PublicWorksManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /obra (alias /obras): obras públicas. El líder crea pedidos de
 * materiales pagados desde la cuenta del país; cualquier jugador entrega el item
 * y cobra por unidad. Se cierra al completarse; se pausa si el país no tiene fondos.
 */
public final class PublicWorksCommands {

    private PublicWorksCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("obra")
                .then(literal("crear")
                        .then(argument("cantidad", IntegerArgumentType.integer(1))
                                .then(argument("precioUnidad", IntegerArgumentType.integer(1))
                                        .executes(ctx -> create(ctx, null))
                                        .then(argument("moneda", StringArgumentType.word())
                                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "moneda")))))))
                .then(literal("lista").executes(PublicWorksCommands::list))
                .then(literal("info")
                        .then(argument("id", IntegerArgumentType.integer(1))
                                .executes(PublicWorksCommands::info)))
                .then(literal("entregar")
                        .then(argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> deliver(ctx, Integer.MAX_VALUE))
                                .then(argument("cantidad", IntegerArgumentType.integer(1))
                                        .executes(ctx -> deliver(ctx, IntegerArgumentType.getInteger(ctx, "cantidad"))))))
                .then(literal("cerrar")
                        .then(argument("id", IntegerArgumentType.integer(1))
                                .executes(PublicWorksCommands::close))));
        dispatcher.register(literal("obras").redirect(node));
    }

    // ---- Helpers ----

    private static Group leaderCountry(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(p.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return null;
        }
        if (!com.realeconomy.politics.Governance.isGovernment(p.getServer(), g, p.getUuid())) {
            ctx.getSource().sendError(Text.literal(
                    com.realeconomy.politics.Governance.notGovernmentMessage(p.getServer(), g)));
            return null;
        }
        return g;
    }

    private static String currencyName(MinecraftServer server, String currencyId) {
        Currency c = CurrencyManager.get(server).getById(currencyId);
        return c != null ? c.getName() : currencyId;
    }

    private static String itemName(String itemId) {
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        return new ItemStack(item).getName().getString();
    }

    private static int countItem(PlayerInventory inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void removeItem(PlayerInventory inv, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) {
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
            }
        }
        inv.markDirty();
    }

    // ---- Comandos ----

    private static int create(CommandContext<ServerCommandSource> ctx, String tag) throws CommandSyntaxException {
        Group g = leaderCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Sostén en la mano el item que quieres pedir."));
            return 0;
        }
        Currency c;
        if (tag != null) {
            Group cg = GroupManager.get(server).getByTag(tag);
            c = cg == null ? null : CurrencyManager.get(server).getByGroup(cg.getId());
            if (c == null) {
                ctx.getSource().sendError(Text.literal("No existe un país con esas siglas o no tiene moneda."));
                return 0;
            }
        } else {
            c = CurrencyManager.get(server).getByGroup(g.getId());
            if (c == null) {
                ctx.getSource().sendError(Text.literal("Tu país no tiene moneda."));
                return 0;
            }
        }
        int qty = IntegerArgumentType.getInteger(ctx, "cantidad");
        int price = IntegerArgumentType.getInteger(ctx, "precioUnidad");
        String itemId = Registries.ITEM.getId(hand.getItem()).toString();
        PublicWork w = PublicWorksManager.get(server).create(g.getId(), itemId, qty, price, c.getId());
        ctx.getSource().sendFeedback(() -> Text.literal("Obra pública #" + w.getId() + " creada: "
                + qty + "x " + itemName(itemId) + " a " + price + " " + c.getName()
                + "/unidad. Los jugadores entregan con /obra entregar " + w.getId() + ".")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        PublicWorksManager pw = PublicWorksManager.get(server);
        GroupManager gm = GroupManager.get(server);
        BalanceManager bm = BalanceManager.get(server);
        List<PublicWork> all = pw.all();
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("=== Obras públicas abiertas (" + all.size() + ") ===");
            if (all.isEmpty()) {
                t.append(Text.literal("\n (no hay obras ahora mismo)"));
            }
            for (PublicWork w : all) {
                Group g = gm.getById(w.getCountryId());
                String tagStr = g != null ? "[" + g.getTag() + "] " : "";
                long funds = g != null ? bm.get(FinanceManager.accountUuid(g), w.getCurrencyId()) : 0L;
                boolean paused = funds < w.getPricePerUnit();
                t.append(Text.literal("\n#" + w.getId() + " " + tagStr + itemName(w.getItemId())
                        + " " + w.getDelivered() + "/" + w.getRequired()
                        + " · " + w.getPricePerUnit() + " " + currencyName(server, w.getCurrencyId()) + "/ud"
                        + (paused ? " (sin fondos)" : "")));
            }
            return t;
        }, false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int id = IntegerArgumentType.getInteger(ctx, "id");
        PublicWork w = PublicWorksManager.get(server).get(id);
        if (w == null) {
            ctx.getSource().sendError(Text.literal("No existe la obra pública #" + id + "."));
            return 0;
        }
        Group g = GroupManager.get(server).getById(w.getCountryId());
        long funds = g != null ? BalanceManager.get(server).get(FinanceManager.accountUuid(g), w.getCurrencyId()) : 0L;
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Obra pública #" + w.getId() + (g != null ? " de [" + g.getTag() + "]" : ""));
            t.append(Text.literal("\n - Material: " + itemName(w.getItemId())));
            t.append(Text.literal("\n - Progreso: " + w.getDelivered() + "/" + w.getRequired()
                    + " (faltan " + w.getRemaining() + ")"));
            t.append(Text.literal("\n - Pago: " + w.getPricePerUnit() + " " + currencyName(server, w.getCurrencyId()) + "/unidad"));
            t.append(Text.literal("\n - Fondos del país: " + funds
                    + (funds < w.getPricePerUnit() ? " (sin fondos: pausada)" : "")));
            return t;
        }, false);
        return 1;
    }

    private static int deliver(CommandContext<ServerCommandSource> ctx, int requested) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        int id = IntegerArgumentType.getInteger(ctx, "id");
        PublicWorksManager pw = PublicWorksManager.get(server);
        PublicWork w = pw.get(id);
        if (w == null) {
            ctx.getSource().sendError(Text.literal("No existe la obra pública #" + id + "."));
            return 0;
        }
        Group g = GroupManager.get(server).getById(w.getCountryId());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("El país de esta obra ya no existe."));
            return 0;
        }
        Item item = Registries.ITEM.get(Identifier.of(w.getItemId()));
        PlayerInventory inv = player.getInventory();
        int have = countItem(inv, item);
        if (have <= 0) {
            ctx.getSource().sendError(Text.literal("No llevas " + itemName(w.getItemId()) + " para entregar."));
            return 0;
        }
        BalanceManager bm = BalanceManager.get(server);
        UUID account = FinanceManager.accountUuid(g);
        long funds = bm.get(account, w.getCurrencyId());
        long affordable = w.getPricePerUnit() > 0 ? funds / w.getPricePerUnit() : 0;
        if (affordable <= 0) {
            ctx.getSource().sendError(Text.literal("La obra no tiene fondos ahora mismo (pausada). Inténtalo más tarde."));
            return 0;
        }
        int units = (int) Math.min(Math.min(requested, have), Math.min(w.getRemaining(), affordable));
        if (units <= 0) {
            ctx.getSource().sendError(Text.literal("No se puede entregar nada (¿obra completa?). Usa /obra info " + id + "."));
            return 0;
        }
        long pay = (long) units * w.getPricePerUnit();
        if (!bm.subtract(account, w.getCurrencyId(), pay)) {
            ctx.getSource().sendError(Text.literal("La obra no tiene fondos suficientes ahora mismo."));
            return 0;
        }
        removeItem(inv, item, units);
        bm.add(player.getUuid(), w.getCurrencyId(), pay);
        w.addDelivered(units);
        int deliveredUnits = units;
        boolean complete = w.isComplete();
        if (complete) {
            pw.remove(id);
        } else {
            pw.markDirty();
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Entregadas " + deliveredUnits + "x " + itemName(w.getItemId())
                + ". Cobras " + pay + " " + currencyName(server, w.getCurrencyId()) + "."
                + (complete ? " ¡Obra #" + id + " completada!" : " Progreso: " + w.getDelivered() + "/" + w.getRequired()))
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int close(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = leaderCountry(ctx);
        if (g == null) {
            return 0;
        }
        int id = IntegerArgumentType.getInteger(ctx, "id");
        PublicWorksManager pw = PublicWorksManager.get(ctx.getSource().getServer());
        PublicWork w = pw.get(id);
        if (w == null || !w.getCountryId().equals(g.getId())) {
            ctx.getSource().sendError(Text.literal("Tu país no tiene una obra pública con el id #" + id + "."));
            return 0;
        }
        pw.remove(id);
        ctx.getSource().sendFeedback(() -> Text.literal("Obra pública #" + id + " cerrada.").formatted(Formatting.YELLOW), false);
        return 1;
    }
}
