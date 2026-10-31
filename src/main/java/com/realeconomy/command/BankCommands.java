package com.realeconomy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.bank.BankActions;
import com.realeconomy.bank.BankEntities;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Comandos del banco: crear/borrar el NPC (op) y transferir/cambiar moneda (jugadores). */
public final class BankCommands {

    private BankCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("bancocrear")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(BankCommands::create));

        dispatcher.register(literal("bancoborrar")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(BankCommands::remove));

        // Comando de jugador: /banco transferir <jugador> <siglasMoneda> <cantidad>
        //                     /banco cambiar <siglasOrigen> <siglasDestino> <cantidad>
        dispatcher.register(literal("banco")
                .then(literal("transferir")
                        .then(argument("jugador", StringArgumentType.word())
                                .then(argument("moneda", StringArgumentType.word())
                                        .then(argument("cantidad", LongArgumentType.longArg(1))
                                                .executes(BankCommands::transfer)))))
                .then(literal("cambiar")
                        .then(argument("origen", StringArgumentType.word())
                                .then(argument("destino", StringArgumentType.word())
                                        .then(argument("cantidad", LongArgumentType.longArg(1))
                                                .executes(BankCommands::exchange))))));
    }

    /** Resuelve el id de moneda a partir de las siglas de un país. */
    private static String currencyIdByTag(MinecraftServer server, String tag) {
        Group g = GroupManager.get(server).getByTag(tag);
        if (g == null) {
            return null;
        }
        Currency c = CurrencyManager.get(server).getByGroup(g.getId());
        return c == null ? null : c.getId();
    }

    private static int transfer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        String targetName = StringArgumentType.getString(ctx, "jugador");
        String tag = StringArgumentType.getString(ctx, "moneda");
        long amount = LongArgumentType.getLong(ctx, "cantidad");

        String currencyId = currencyIdByTag(server, tag);
        if (currencyId == null) {
            ctx.getSource().sendError(Text.literal("No existe la moneda del país [" + tag + "]."));
            return 0;
        }
        Optional<GameProfile> profile = server.getUserCache() == null
                ? Optional.empty() : server.getUserCache().findByName(targetName);
        if (profile.isEmpty()) {
            ctx.getSource().sendError(Text.literal("No se encuentra al jugador '" + targetName + "'."));
            return 0;
        }
        UUID targetId = profile.get().getId();
        if (targetId.equals(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("No puedes transferirte a ti mismo."));
            return 0;
        }
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(player.getUuid(), currencyId, amount)) {
            ctx.getSource().sendError(Text.literal("Saldo insuficiente."));
            return 0;
        }
        long tax = BankActions.transferTax(server, currencyId, amount);
        bm.add(targetId, currencyId, amount - tax);
        long net = amount - tax;
        Currency c = CurrencyManager.get(server).getById(currencyId);
        String name = c != null ? c.getName() : currencyId;
        ctx.getSource().sendFeedback(() -> Text.literal("Transferidas " + amount + " " + name + " a " + targetName
                + (tax > 0 ? " (impuesto " + tax + ")" : "") + ".").formatted(Formatting.GREEN), false);
        ServerPlayerEntity tp = server.getPlayerManager().getPlayer(targetId);
        if (tp != null) {
            tp.sendMessage(Text.literal("Has recibido " + net + " " + name + " de "
                    + player.getNameForScoreboard() + ".").formatted(Formatting.GREEN));
        }
        return 1;
    }

    private static int exchange(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        if (!BankEntities.isNearBank(player)) {
            ctx.getSource().sendError(Text.literal("Tienes que estar junto a un banco para cambiar moneda."));
            return 0;
        }
        String origin = currencyIdByTag(server, StringArgumentType.getString(ctx, "origen"));
        String dest = currencyIdByTag(server, StringArgumentType.getString(ctx, "destino"));
        long amount = LongArgumentType.getLong(ctx, "cantidad");
        if (origin == null || dest == null) {
            ctx.getSource().sendError(Text.literal("Siglas de moneda no válidas (origen/destino)."));
            return 0;
        }
        BankActions.exchange(player, origin, dest, amount);
        return 1;
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        BankEntities.spawn(player);
        ctx.getSource().sendFeedback(() -> Text.literal("Banco creado aquí. Haz clic derecho para usarlo."), true);
        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        double range = Math.max(8.0, ModConfig.get().bankRange);
        boolean removed = BankEntities.removeNearest(player.getServerWorld(), player.getPos(), range);
        if (removed) {
            ctx.getSource().sendFeedback(() -> Text.literal("Banco eliminado."), true);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("No hay ningún banco cerca (a menos de " + (int) range + " bloques)."));
        return 0;
    }
}
