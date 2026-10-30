package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Comandos /moneda (alias /currency) para que el líder gestione la moneda del grupo. */
public final class CurrencyCommands {

    private CurrencyCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("moneda")
                .then(literal("crear")
                        .then(argument("nombre", StringArgumentType.word())
                                .executes(ctx -> create(ctx, null))
                                .then(argument("url", StringArgumentType.greedyString())
                                        .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "url"))))))
                .then(literal("icono")
                        .then(argument("url", StringArgumentType.greedyString())
                                .executes(CurrencyCommands::setIcon)))
                .then(literal("nombre")
                        .then(argument("nombre", StringArgumentType.greedyString())
                                .executes(CurrencyCommands::setName)))
                .then(literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(argument("siglas", StringArgumentType.word())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "siglas")))));

        var node = dispatcher.register(root);
        dispatcher.register(literal("currency").redirect(node));
    }

    private static Group requireLeaderGroup(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager gm = GroupManager.get(ctx.getSource().getServer());
        Group g = gm.getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún grupo."));
            return null;
        }
        if (!g.isLeader(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el líder del grupo puede gestionar la moneda."));
            return null;
        }
        return g;
    }

    private static int create(CommandContext<ServerCommandSource> ctx, String url) throws CommandSyntaxException {
        Group g = requireLeaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        CurrencyManager cm = CurrencyManager.get(ctx.getSource().getServer());
        if (cm.groupHasCurrency(g.getId())) {
            ctx.getSource().sendError(Text.literal("Tu grupo ya tiene una moneda. Usa /moneda nombre o /moneda icono para cambiarla."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        Currency c = cm.createForGroup(g, name, url);
        ctx.getSource().sendFeedback(() -> Text.literal("Moneda creada: " + c.getName()
                + (url == null ? " (icono por defecto)" : " (descargando icono...)")), true);
        return 1;
    }

    private static int setIcon(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = requireLeaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        CurrencyManager cm = CurrencyManager.get(ctx.getSource().getServer());
        Currency c = cm.getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Tu grupo aún no tiene moneda. Créala con /moneda crear <nombre> <url>."));
            return 0;
        }
        String url = StringArgumentType.getString(ctx, "url");
        cm.setIcon(c, url);
        ctx.getSource().sendFeedback(() -> Text.literal("Descargando el nuevo icono..."), false);
        return 1;
    }

    private static int setName(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = requireLeaderGroup(ctx);
        if (g == null) {
            return 0;
        }
        CurrencyManager cm = CurrencyManager.get(ctx.getSource().getServer());
        Currency c = cm.getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Tu grupo aún no tiene moneda."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        cm.setName(c, name);
        ctx.getSource().sendFeedback(() -> Text.literal("Nombre de la moneda actualizado a: " + name), false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx, String tag) throws CommandSyntaxException {
        GroupManager gm = GroupManager.get(ctx.getSource().getServer());
        CurrencyManager cm = CurrencyManager.get(ctx.getSource().getServer());
        Group g;
        if (tag != null) {
            g = gm.getByTag(tag);
            if (g == null) {
                ctx.getSource().sendError(Text.literal("No existe un grupo con esas siglas."));
                return 0;
            }
        } else {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            g = gm.getByPlayer(player.getUuid());
            if (g == null) {
                ctx.getSource().sendError(Text.literal("No perteneces a ningún grupo. Usa /moneda info <siglas>."));
                return 0;
            }
        }
        Currency c = cm.getByGroup(g.getId());
        if (c == null) {
            ctx.getSource().sendError(Text.literal("Ese grupo no tiene moneda."));
            return 0;
        }
        final Group fg = g;
        ctx.getSource().sendFeedback(() -> Text.literal("Moneda de ")
                .append(Text.literal("[" + fg.getTag() + "]").formatted(fg.getColor()))
                .append(Text.literal(": " + c.getName()
                        + (c.getIconUrl() == null ? " (icono por defecto)" : ""))), false);
        return 1;
    }
}
