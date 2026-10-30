package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.party.Party;
import com.realeconomy.party.PartyManager;
import com.realeconomy.townhall.TownhallEntities;
import com.realeconomy.util.ColorUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /partido (alias /party): partidos políticos dentro de un país. Crear un
 * partido requiere estar junto a un ayuntamiento y pagar (va a la cuenta del país).
 * Unirse es libre, pero solo a partidos de tu propio país.
 */
public final class PartyCommands {

    private static final SuggestionProvider<ServerCommandSource> COLOR_SUGGESTIONS =
            (context, builder) -> CommandSource.suggestMatching(ColorUtil.suggestions(), builder);

    private PartyCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("partido")
                .then(literal("crear")
                        .then(argument("color", StringArgumentType.word())
                                .suggests(COLOR_SUGGESTIONS)
                                .then(argument("nombre", StringArgumentType.greedyString())
                                        .executes(PartyCommands::create))))
                .then(literal("unirse")
                        .then(argument("nombre", StringArgumentType.greedyString())
                                .executes(PartyCommands::join)))
                .then(literal("salir").executes(PartyCommands::leave))
                .then(literal("lista").executes(PartyCommands::list))
                .then(literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(argument("nombre", StringArgumentType.greedyString())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "nombre")))))
                .then(literal("color")
                        .then(argument("color", StringArgumentType.word())
                                .suggests(COLOR_SUGGESTIONS)
                                .executes(PartyCommands::setColor)))
                .then(literal("borrar").executes(PartyCommands::disband));

        var node = dispatcher.register(root);
        dispatcher.register(literal("party").redirect(node));
    }

    private static Group country(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país. Crea o únete a uno con /pais."));
        }
        return g;
    }

    private static Text label(Party p) {
        return Text.literal(p.getName()).formatted(p.getColor());
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        Group g = country(ctx);
        if (g == null) {
            return 0;
        }
        PartyManager pm = PartyManager.get(server);
        if (pm.getByPlayer(player.getUuid()) != null) {
            ctx.getSource().sendError(Text.literal("Ya estás en un partido. Sal con /partido salir antes de crear otro."));
            return 0;
        }
        if (!TownhallEntities.isNear(player)) {
            ctx.getSource().sendError(Text.literal("Debes estar junto a un ayuntamiento para fundar un partido."));
            return 0;
        }
        Formatting color = ColorUtil.parse(StringArgumentType.getString(ctx, "color"));
        if (color == null) {
            ctx.getSource().sendError(Text.literal("Color no válido. Prueba con: " + ColorUtil.displayNames()));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre").trim();
        if (name.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Indica un nombre para el partido."));
            return 0;
        }
        if (pm.getByNameInCountry(g.getId(), name) != null) {
            ctx.getSource().sendError(Text.literal("Ya existe un partido con ese nombre en tu país."));
            return 0;
        }
        // Cobro de la creación: va a la cuenta del país, en la moneda configurada.
        Currency currency = partyCurrency(server, g);
        if (currency == null) {
            ctx.getSource().sendError(Text.literal("Tu país necesita una moneda. El líder la crea con /moneda crear <nombre>."));
            return 0;
        }
        int price = ModConfig.get().partyPrice;
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(player.getUuid(), currency.getId(), price)) {
            ctx.getSource().sendError(Text.literal("Saldo insuficiente. Fundar un partido cuesta " + price + " " + currency.getName() + "."));
            return 0;
        }
        bm.add(FinanceManager.accountUuid(g), currency.getId(), price);
        Party p = pm.createParty(g.getId(), name, color, player.getUuid(), player.getNameForScoreboard());
        ctx.getSource().sendFeedback(() -> Text.literal("Partido fundado: ").append(label(p))
                .append(Text.literal(" (pagados " + price + " " + currency.getName() + " a la cuenta del país)")), true);
        return 1;
    }

    /** Moneda con la que se paga crear un partido: la configurada, o la del propio país. */
    private static Currency partyCurrency(MinecraftServer server, Group ownCountry) {
        String tag = ModConfig.get().partyCurrencyTag;
        CurrencyManager cm = CurrencyManager.get(server);
        if (tag != null && !tag.isBlank()) {
            Group other = GroupManager.get(server).getByTag(tag);
            return other == null ? null : cm.getByGroup(other.getId());
        }
        return cm.getByGroup(ownCountry.getId());
    }

    private static int join(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = country(ctx);
        if (g == null) {
            return 0;
        }
        PartyManager pm = PartyManager.get(ctx.getSource().getServer());
        if (pm.getByPlayer(player.getUuid()) != null) {
            ctx.getSource().sendError(Text.literal("Ya estás en un partido. Sal con /partido salir antes de unirte a otro."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre").trim();
        Party p = pm.getByNameInCountry(g.getId(), name);
        if (p == null) {
            ctx.getSource().sendError(Text.literal("No existe ese partido en tu país. Mira /partido lista."));
            return 0;
        }
        pm.joinParty(p, player.getUuid(), player.getNameForScoreboard());
        ctx.getSource().sendFeedback(() -> Text.literal("Te has unido al partido ").append(label(p)), false);
        return 1;
    }

    private static int leave(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        PartyManager pm = PartyManager.get(ctx.getSource().getServer());
        Party p = pm.getByPlayer(player.getUuid());
        if (p == null) {
            ctx.getSource().sendError(Text.literal("No estás en ningún partido."));
            return 0;
        }
        pm.leaveParty(player.getUuid());
        ctx.getSource().sendFeedback(() -> Text.literal("Has salido del partido ").append(label(p)), false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = country(ctx);
        if (g == null) {
            return 0;
        }
        List<Party> parties = PartyManager.get(ctx.getSource().getServer()).partiesOf(g.getId());
        if (parties.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Tu país no tiene partidos todavía. Funda uno con /partido crear <color> <nombre>."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Partidos de ")
                    .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor())).append(":");
            for (Party p : parties) {
                t.append(Text.literal("\n - ")).append(label(p))
                        .append(Text.literal(" (" + p.size() + " miembros)"));
            }
            return t;
        }, false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx, String nameArg) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        PartyManager pm = PartyManager.get(ctx.getSource().getServer());
        Party p;
        if (nameArg != null) {
            Group g = country(ctx);
            if (g == null) {
                return 0;
            }
            p = pm.getByNameInCountry(g.getId(), nameArg.trim());
            if (p == null) {
                ctx.getSource().sendError(Text.literal("No existe ese partido en tu país."));
                return 0;
            }
        } else {
            p = pm.getByPlayer(player.getUuid());
            if (p == null) {
                ctx.getSource().sendError(Text.literal("No estás en ningún partido. Usa /partido info <nombre> para ver otro."));
                return 0;
            }
        }
        final Party fp = p;
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("=== Partido ").append(label(fp)).append(Text.literal(" ==="));
            t.append(Text.literal("\nColor: " + fp.getColor().getName()));
            t.append(Text.literal("\nFundador: " + fp.getMemberNames().get(fp.getLeader())));
            t.append(Text.literal("\nMiembros (" + fp.size() + "): " + String.join(", ", fp.getMemberNames().values())));
            return t;
        }, false);
        return 1;
    }

    private static int setColor(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        PartyManager pm = PartyManager.get(ctx.getSource().getServer());
        Party p = pm.getByPlayer(player.getUuid());
        if (p == null || !p.isLeader(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el fundador del partido puede cambiar su color."));
            return 0;
        }
        Formatting color = ColorUtil.parse(StringArgumentType.getString(ctx, "color"));
        if (color == null) {
            ctx.getSource().sendError(Text.literal("Color no válido. Prueba con: " + ColorUtil.displayNames()));
            return 0;
        }
        p.setColor(color);
        pm.markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Color del partido actualizado: ").append(label(p)), false);
        return 1;
    }

    private static int disband(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        PartyManager pm = PartyManager.get(ctx.getSource().getServer());
        Party p = pm.getByPlayer(player.getUuid());
        if (p == null || !p.isLeader(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el fundador del partido puede disolverlo."));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        for (java.util.UUID uuid : p.getMembers()) {
            if (uuid.equals(player.getUuid())) {
                continue;
            }
            ServerPlayerEntity m = server.getPlayerManager().getPlayer(uuid);
            if (m != null) {
                m.sendMessage(Text.literal("Tu partido ").append(label(p)).append(Text.literal(" ha sido disuelto.")));
            }
        }
        pm.disband(p);
        ctx.getSource().sendFeedback(() -> Text.literal("Partido disuelto."), true);
        return 1;
    }
}
