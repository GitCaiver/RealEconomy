package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.util.ColorUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class GroupCommands {

    private static final SuggestionProvider<ServerCommandSource> COLOR_SUGGESTIONS =
            (context, builder) -> CommandSource.suggestMatching(ColorUtil.suggestions(), builder);

    private GroupCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal("group")
                .then(literal("create")
                        .then(argument("siglas", StringArgumentType.word())
                                .then(argument("color", StringArgumentType.word())
                                        .suggests(COLOR_SUGGESTIONS)
                                        .executes(ctx -> create(ctx, null))
                                        .then(argument("nombre", StringArgumentType.greedyString())
                                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "nombre")))))))
                .then(literal("invite")
                        .then(argument("jugador", EntityArgumentType.player())
                                .executes(GroupCommands::invite)))
                .then(literal("accept")
                        .executes(ctx -> accept(ctx, null))
                        .then(argument("siglas", StringArgumentType.word())
                                .executes(ctx -> accept(ctx, StringArgumentType.getString(ctx, "siglas")))))
                .then(literal("deny")
                        .then(argument("siglas", StringArgumentType.word())
                                .executes(GroupCommands::deny)))
                .then(literal("kick")
                        .then(argument("jugador", StringArgumentType.word())
                                .executes(GroupCommands::kick)))
                .then(literal("leave")
                        .executes(GroupCommands::leave))
                .then(literal("transfer")
                        .then(argument("jugador", StringArgumentType.word())
                                .executes(GroupCommands::transfer)))
                .then(literal("disband")
                        .executes(GroupCommands::disband))
                .then(literal("settag")
                        .then(argument("siglas", StringArgumentType.word())
                                .executes(GroupCommands::setTag)))
                .then(literal("setcolor")
                        .then(argument("color", StringArgumentType.word())
                                .suggests(COLOR_SUGGESTIONS)
                                .executes(GroupCommands::setColor)))
                .then(literal("setname")
                        .then(argument("nombre", StringArgumentType.greedyString())
                                .executes(GroupCommands::setName)))
                .then(literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(argument("siglas", StringArgumentType.word())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "siglas")))))
                .then(literal("list")
                        .executes(GroupCommands::list));

        com.mojang.brigadier.tree.LiteralCommandNode<ServerCommandSource> node = dispatcher.register(root);
        // Alias en español.
        dispatcher.register(literal("grupo").redirect(node));
    }

    // ---- Subcomandos ----

    private static int create(CommandContext<ServerCommandSource> ctx, String nameArg) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        if (mgr.getByPlayer(player.getUuid()) != null) {
            error(ctx, "Ya perteneces a un grupo. Sal de él con /group leave antes de crear otro.");
            return 0;
        }

        String tag = StringArgumentType.getString(ctx, "siglas");
        String tagError = GroupManager.validateTag(tag);
        if (tagError != null) {
            error(ctx, tagError);
            return 0;
        }
        if (mgr.tagExists(tag)) {
            error(ctx, "Ya existe un grupo con las siglas [" + GroupManager.normalizeTag(tag) + "].");
            return 0;
        }

        String colorName = StringArgumentType.getString(ctx, "color");
        Formatting color = ColorUtil.parse(colorName);
        if (color == null) {
            error(ctx, "Color no válido. Prueba con: " + ColorUtil.displayNames());
            return 0;
        }

        String name = (nameArg == null || nameArg.isBlank()) ? GroupManager.normalizeTag(tag) : nameArg;
        Group g = mgr.createGroup(name, tag, color, player);

        ctx.getSource().sendFeedback(() -> Text.literal("Grupo creado: ")
                .append(groupLabel(g)), true);
        return 1;
    }

    private static int invite(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null) {
            error(ctx, "No perteneces a ningún grupo.");
            return 0;
        }
        if (!g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede invitar.");
            return 0;
        }

        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "jugador");
        if (mgr.getByPlayer(target.getUuid()) != null) {
            error(ctx, target.getNameForScoreboard() + " ya pertenece a un grupo.");
            return 0;
        }
        if (target.getUuid().equals(player.getUuid())) {
            error(ctx, "No puedes invitarte a ti mismo.");
            return 0;
        }

        mgr.invite(g, target.getUuid());
        ctx.getSource().sendFeedback(() -> Text.literal("Has invitado a " + target.getNameForScoreboard() + " a ")
                .append(groupLabel(g)), false);
        target.sendMessage(Text.literal("Has recibido una invitación de ")
                .append(groupLabel(g))
                .append(Text.literal(". Usa /group accept " + g.getTag() + " para unirte o /group deny " + g.getTag() + " para rechazarla.")));
        return 1;
    }

    private static int accept(CommandContext<ServerCommandSource> ctx, String tagArg) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        if (mgr.getByPlayer(player.getUuid()) != null) {
            error(ctx, "Ya perteneces a un grupo.");
            return 0;
        }

        Group g;
        if (tagArg != null) {
            g = mgr.getByTag(tagArg);
            if (g == null || !mgr.hasInvite(player.getUuid(), g)) {
                error(ctx, "No tienes una invitación de ese grupo.");
                return 0;
            }
        } else {
            var ids = mgr.getInvites(player.getUuid());
            if (ids.isEmpty()) {
                error(ctx, "No tienes invitaciones pendientes.");
                return 0;
            }
            if (ids.size() > 1) {
                error(ctx, "Tienes varias invitaciones. Especifica las siglas: /group accept <siglas>.");
                return 0;
            }
            g = mgr.getById(ids.iterator().next());
            if (g == null) {
                error(ctx, "La invitación ya no es válida.");
                return 0;
            }
        }

        mgr.addMember(g, player.getUuid(), player.getNameForScoreboard());
        Group finalGroup = g;
        ctx.getSource().sendFeedback(() -> Text.literal("Te has unido a ").append(groupLabel(finalGroup)), false);
        notifyMembers(ctx.getSource().getServer(), g, Text.literal(player.getNameForScoreboard() + " se ha unido al grupo."), player.getUuid());
        return 1;
    }

    private static int deny(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        String tag = StringArgumentType.getString(ctx, "siglas");
        Group g = mgr.getByTag(tag);
        if (g == null || !mgr.hasInvite(player.getUuid(), g)) {
            error(ctx, "No tienes una invitación de ese grupo.");
            return 0;
        }
        mgr.removeInvite(player.getUuid(), g);
        ctx.getSource().sendFeedback(() -> Text.literal("Invitación rechazada."), false);
        return 1;
    }

    private static int kick(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        GroupManager mgr = GroupManager.get(server);

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null) {
            error(ctx, "No perteneces a ningún grupo.");
            return 0;
        }
        if (!g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede expulsar.");
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "jugador");
        UUID targetUuid = resolveMember(g, name);
        if (targetUuid == null) {
            error(ctx, "Ese jugador no está en tu grupo.");
            return 0;
        }
        if (targetUuid.equals(player.getUuid())) {
            error(ctx, "No puedes expulsarte a ti mismo. Usa /group disband para disolver el grupo.");
            return 0;
        }

        mgr.removeMember(g, targetUuid);
        ctx.getSource().sendFeedback(() -> Text.literal("Has expulsado a " + name + " del grupo."), false);
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(Text.literal("Has sido expulsado de ").append(groupLabel(g)));
        }
        return 1;
    }

    private static int leave(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null) {
            error(ctx, "No perteneces a ningún grupo.");
            return 0;
        }
        if (g.isLeader(player.getUuid())) {
            error(ctx, "Eres el líder. Transfiere el liderazgo (/group transfer <jugador>) o disuelve el grupo (/group disband).");
            return 0;
        }

        mgr.removeMember(g, player.getUuid());
        ctx.getSource().sendFeedback(() -> Text.literal("Has salido de ").append(groupLabel(g)), false);
        notifyMembers(ctx.getSource().getServer(), g, Text.literal(player.getNameForScoreboard() + " ha salido del grupo."), player.getUuid());
        return 1;
    }

    private static int transfer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede transferir el liderazgo.");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "jugador");
        UUID targetUuid = resolveMember(g, name);
        if (targetUuid == null) {
            error(ctx, "Ese jugador no está en tu grupo.");
            return 0;
        }
        mgr.setLeader(g, targetUuid);
        ctx.getSource().sendFeedback(() -> Text.literal("Has transferido el liderazgo a " + name + "."), false);
        return 1;
    }

    private static int disband(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede disolver el grupo.");
            return 0;
        }
        notifyMembers(ctx.getSource().getServer(), g, Text.literal("Tu grupo ha sido disuelto por el líder."), player.getUuid());
        mgr.disband(g);
        ctx.getSource().sendFeedback(() -> Text.literal("Grupo disuelto."), true);
        return 1;
    }

    private static int setTag(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede cambiar las siglas.");
            return 0;
        }
        String tag = StringArgumentType.getString(ctx, "siglas");
        String tagError = GroupManager.validateTag(tag);
        if (tagError != null) {
            error(ctx, tagError);
            return 0;
        }
        Group existing = mgr.getByTag(tag);
        if (existing != null && !existing.getId().equals(g.getId())) {
            error(ctx, "Ya existe un grupo con esas siglas.");
            return 0;
        }
        mgr.setTag(g, tag);
        ctx.getSource().sendFeedback(() -> Text.literal("Siglas actualizadas a ").append(groupLabel(g)), false);
        return 1;
    }

    private static int setColor(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede cambiar el color.");
            return 0;
        }
        Formatting color = ColorUtil.parse(StringArgumentType.getString(ctx, "color"));
        if (color == null) {
            error(ctx, "Color no válido. Prueba con: " + ColorUtil.displayNames());
            return 0;
        }
        mgr.setColor(g, color);
        ctx.getSource().sendFeedback(() -> Text.literal("Color actualizado: ").append(groupLabel(g)), false);
        return 1;
    }

    private static int setName(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede cambiar el nombre.");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        mgr.setName(g, name);
        ctx.getSource().sendFeedback(() -> Text.literal("Nombre actualizado: ").append(groupLabel(g)), false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx, String tagArg) throws CommandSyntaxException {
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        Group g;
        if (tagArg != null) {
            g = mgr.getByTag(tagArg);
            if (g == null) {
                error(ctx, "No existe un grupo con esas siglas.");
                return 0;
            }
        } else {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            g = mgr.getByPlayer(player.getUuid());
            if (g == null) {
                error(ctx, "No perteneces a ningún grupo. Usa /group info <siglas> para ver otro.");
                return 0;
            }
        }

        final Group fg = g;
        ctx.getSource().sendFeedback(() -> {
            MutableBuilder b = new MutableBuilder();
            b.line(Text.literal("=== Grupo ").append(groupLabel(fg)).append(Text.literal(" ===")));
            b.line(Text.literal("Nombre: " + fg.getName()));
            b.line(Text.literal("Color: " + fg.getColor().getName()));
            b.line(Text.literal("Líder: " + fg.getMemberName(fg.getLeader())));
            b.line(Text.literal("Miembros (" + fg.size() + "): " + String.join(", ", fg.getMemberNames().values())));
            return b.build();
        }, false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        var groups = mgr.getGroups();
        if (groups.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("No hay grupos todavía."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> {
            MutableBuilder b = new MutableBuilder();
            b.line(Text.literal("=== Grupos (" + groups.size() + ") ==="));
            for (Group g : groups) {
                b.line(groupLabel(g).copy().append(Text.literal(" - " + g.getName() + " (" + g.size() + " miembros)")));
            }
            return b.build();
        }, false);
        return 1;
    }

    // ---- Utilidades ----

    /** Texto "[TAG]" con el color del grupo. */
    private static Text groupLabel(Group g) {
        return Text.literal("[" + g.getTag() + "]").formatted(g.getColor());
    }

    private static UUID resolveMember(Group g, String name) {
        for (var e : g.getMemberNames().entrySet()) {
            if (e.getValue() != null && e.getValue().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static void notifyMembers(MinecraftServer server, Group g, Text message, UUID except) {
        for (UUID uuid : g.getMembers()) {
            if (uuid.equals(except)) {
                continue;
            }
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    private static void error(CommandContext<ServerCommandSource> ctx, String message) {
        ctx.getSource().sendError(Text.literal(message));
    }

    /** Pequeño ayudante para construir mensajes multilínea. */
    private static final class MutableBuilder {
        private net.minecraft.text.MutableText text = Text.empty();
        private boolean first = true;

        void line(Text line) {
            if (!first) {
                text.append(Text.literal("\n"));
            }
            text.append(line);
            first = false;
        }

        Text build() {
            return text;
        }
    }
}
