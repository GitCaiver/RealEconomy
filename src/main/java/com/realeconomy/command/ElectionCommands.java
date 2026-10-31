package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.realeconomy.config.ModConfig;
import com.realeconomy.election.Election;
import com.realeconomy.election.ElectionManager;
import com.realeconomy.election.ElectionRecord;
import com.realeconomy.election.UrnEntities;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.party.Party;
import com.realeconomy.party.PartyManager;
import com.realeconomy.politics.Governance;
import com.realeconomy.politics.PoliticalForms;
import com.realeconomy.util.TimeUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Comandos de elecciones (/elecciones) y de las urnas (/urnacrear, /urnaborrar). */
public final class ElectionCommands {

    private ElectionCommands() {
    }

    private static final SuggestionProvider<ServerCommandSource> PARTY_SUGGESTIONS = (ctx, builder) -> {
        try {
            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
            Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(p.getUuid());
            if (g != null) {
                for (Party party : PartyManager.get(ctx.getSource().getServer()).partiesOf(g.getId())) {
                    builder.suggest(party.getName());
                }
            }
        } catch (Exception ignored) {
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("elecciones")
                .executes(ElectionCommands::status)
                .then(literal("info").executes(ElectionCommands::status))
                .then(literal("votar")
                        .then(argument("partido", StringArgumentType.greedyString())
                                .suggests(PARTY_SUGGESTIONS)
                                .executes(ElectionCommands::vote)))
                .then(literal("historial")
                        .executes(ctx -> history(ctx, null))
                        .then(argument("siglas", StringArgumentType.word())
                                .executes(ctx -> history(ctx, StringArgumentType.getString(ctx, "siglas"))))));
        dispatcher.register(literal("eleccion").redirect(node));

        // Urnas: las coloca el gobierno o un op.
        dispatcher.register(literal("urnacrear").executes(ElectionCommands::urnCreate));
        dispatcher.register(literal("urnaborrar").executes(ElectionCommands::urnRemove));
    }

    private static boolean canManageUrn(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
        if (ctx.getSource().hasPermissionLevel(2)) {
            return true;
        }
        Group g = GroupManager.get(player.getServer()).getByPlayer(player.getUuid());
        return g != null && Governance.isGovernment(player.getServer(), g, player.getUuid());
    }

    private static int urnCreate(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if (!canManageUrn(ctx, player)) {
            ctx.getSource().sendError(Text.literal("Solo el gobierno del país o un operador puede colocar urnas."));
            return 0;
        }
        UrnEntities.spawn(player);
        ctx.getSource().sendFeedback(() -> Text.literal("Urna electoral creada aquí. Haz clic derecho para votar."), true);
        return 1;
    }

    private static int urnRemove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if (!canManageUrn(ctx, player)) {
            ctx.getSource().sendError(Text.literal("Solo el gobierno del país o un operador puede quitar urnas."));
            return 0;
        }
        double range = Math.max(8.0, ModConfig.get().urnRange);
        boolean removed = UrnEntities.removeNearest(player.getServerWorld(), player.getPos(), range);
        if (removed) {
            ctx.getSource().sendFeedback(() -> Text.literal("Urna eliminada."), true);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("No hay ninguna urna cerca."));
        return 0;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return 0;
        }
        ElectionManager em = ElectionManager.get(server);
        Election e = em.getActive(g.getId());
        boolean electoral = PoliticalForms.get(g.getForm(), g.getSubtype()).elections;
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Elecciones de ").append(tag(g)).append(Text.literal(":"));
            if (!electoral) {
                t.append(Text.literal("\n Tu forma (" + PoliticalForms.displayName(g.getForm(), g.getSubtype())
                        + ") no celebra elecciones."));
                return t;
            }
            if (e == null) {
                t.append(Text.literal("\n No hay elección abierta. Se vota el día de elecciones ("
                        + ModConfig.get().electionDayOfWeek.toLowerCase(java.util.Locale.ROOT) + ")."));
            } else {
                t.append(Text.literal("\n ABIERTA · cierra en " + TimeUtil.format(Math.max(0, e.getEnd() - System.currentTimeMillis()))
                        + " · votos: " + e.totalVotes()));
                t.append(Text.literal(e.hasVoted(player.getUuid()) ? "\n Ya has votado." : "\n Vota con /elecciones votar <partido> (junto a una urna)."));
            }
            List<Party> parties = PartyManager.get(server).partiesOf(g.getId());
            t.append(Text.literal("\n Partidos (" + parties.size() + "): "));
            StringBuilder sb = new StringBuilder();
            for (Party p : parties) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p.getName());
            }
            t.append(Text.literal(sb.length() == 0 ? "(ninguno)" : sb.toString()));
            return t;
        }, false);
        return 1;
    }

    private static int vote(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        if (!UrnEntities.isNear(player)) {
            ctx.getSource().sendError(Text.literal("Debes estar junto a una urna para votar."));
            return 0;
        }
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "partido");
        Party p = PartyManager.get(server).getByNameInCountry(g.getId(), name);
        if (p == null) {
            ctx.getSource().sendError(Text.literal("No existe un partido llamado \"" + name + "\" en tu país."));
            return 0;
        }
        ElectionManager.VoteResult r = ElectionManager.get(server).vote(server, g, player, p.getId());
        switch (r) {
            case OK -> ctx.getSource().sendFeedback(() -> Text.literal("Voto registrado para " + p.getName() + ".")
                    .formatted(Formatting.GREEN), false);
            case NO_ELECTION -> ctx.getSource().sendError(Text.literal("No hay ninguna elección abierta."));
            case ALREADY_VOTED -> ctx.getSource().sendError(Text.literal("Ya has votado en esta elección."));
            case BAD_PARTY -> ctx.getSource().sendError(Text.literal("Ese partido no existe en tu país."));
        }
        return 1;
    }

    private static int history(CommandContext<ServerCommandSource> ctx, String tagArg) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        GroupManager gm = GroupManager.get(server);
        Group g;
        if (tagArg != null) {
            g = gm.getByTag(tagArg);
            if (g == null) {
                ctx.getSource().sendError(Text.literal("No existe un país con esas siglas."));
                return 0;
            }
        } else {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            g = gm.getByPlayer(player.getUuid());
            if (g == null) {
                ctx.getSource().sendError(Text.literal("No perteneces a ningún país. Usa /elecciones historial <siglas>."));
                return 0;
            }
        }
        final Group fg = g;
        List<ElectionRecord> records = ElectionManager.get(server).historyFor(g.getId());
        long now = System.currentTimeMillis();
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Historial de elecciones de ").append(tag(fg)).append(Text.literal(":"));
            if (records.isEmpty()) {
                t.append(Text.literal("\n (todavía no se ha celebrado ninguna)"));
                return t;
            }
            int from = Math.max(0, records.size() - 10);
            for (int i = records.size() - 1; i >= from; i--) {
                ElectionRecord r = records.get(i);
                t.append(Text.literal("\n · hace " + TimeUtil.format(now - r.time) + " → " + r.winnerName)
                        .formatted(Formatting.GRAY));
            }
            return t;
        }, false);
        return 1;
    }

    private static Text tag(Group g) {
        return Text.literal("[" + g.getTag() + "]").formatted(g.getColor() == null ? Formatting.WHITE : g.getColor());
    }
}
