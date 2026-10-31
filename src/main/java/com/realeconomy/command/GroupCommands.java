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

    private static final SuggestionProvider<ServerCommandSource> FORM_SUGGESTIONS =
            (context, builder) -> {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (com.realeconomy.politics.FormType t : com.realeconomy.politics.FormType.values()) {
                    names.add(t.name().toLowerCase(java.util.Locale.ROOT));
                }
                return CommandSource.suggestMatching(names, builder);
            };

    private static final SuggestionProvider<ServerCommandSource> SUBTYPE_SUGGESTIONS =
            (context, builder) -> {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (com.realeconomy.politics.FormSubtype s : com.realeconomy.politics.FormSubtype.values()) {
                    if (s != com.realeconomy.politics.FormSubtype.NONE) {
                        names.add(s.name().toLowerCase(java.util.Locale.ROOT));
                    }
                }
                return CommandSource.suggestMatching(names, builder);
            };

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
                .then(literal("crear")
                        .executes(GroupCommands::openCreate))
                .then(literal("forma")
                        .executes(GroupCommands::formShow)
                        .then(argument("forma", StringArgumentType.word())
                                .suggests(FORM_SUGGESTIONS)
                                .executes(ctx -> setForm(ctx, null))
                                .then(argument("subtipo", StringArgumentType.word())
                                        .suggests(SUBTYPE_SUGGESTIONS)
                                        .executes(ctx -> setForm(ctx, StringArgumentType.getString(ctx, "subtipo"))))))
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
                .then(literal("gobierno")
                        .executes(GroupCommands::governmentList)
                        .then(literal("lista").executes(GroupCommands::governmentList))
                        .then(literal("add")
                                .then(argument("jugador", StringArgumentType.word())
                                        .executes(ctx -> setMinister(ctx, true))))
                        .then(literal("remove")
                                .then(argument("jugador", StringArgumentType.word())
                                        .executes(ctx -> setMinister(ctx, false)))))
                .then(literal("sucesor")
                        .then(argument("jugador", StringArgumentType.word())
                                .executes(GroupCommands::setHeir)))
                .then(literal("abdicar")
                        .executes(GroupCommands::abdicate))
                .then(literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(argument("siglas", StringArgumentType.word())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "siglas")))))
                .then(literal("list")
                        .executes(GroupCommands::list));

        com.mojang.brigadier.tree.LiteralCommandNode<ServerCommandSource> node = dispatcher.register(root);
        // Los "grupos" son ahora PAÍSES: /pais es el comando principal; se mantienen
        // /grupo y /group como alias para no romper costumbres ni datos existentes.
        dispatcher.register(literal("pais").redirect(node));
        dispatcher.register(literal("paises").redirect(node));
        dispatcher.register(literal("grupo").redirect(node));
    }

    // ---- Subcomandos ----

    private static int create(CommandContext<ServerCommandSource> ctx, String nameArg) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        if (mgr.getByPlayer(player.getUuid()) != null) {
            error(ctx, "Ya perteneces a un país. Sal de él con /pais leave antes de crear otro.");
            return 0;
        }

        String tag = StringArgumentType.getString(ctx, "siglas");
        String tagError = GroupManager.validateTag(tag);
        if (tagError != null) {
            error(ctx, tagError);
            return 0;
        }
        if (mgr.tagExists(tag)) {
            error(ctx, "Ya existe un país con las siglas [" + GroupManager.normalizeTag(tag) + "].");
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

        ctx.getSource().sendFeedback(() -> Text.literal("País creado: ")
                .append(groupLabel(g))
                .append(Text.literal(" · forma: "
                        + com.realeconomy.politics.PoliticalForms.displayName(g.getForm(), g.getSubtype())
                        + " (cámbiala con /pais forma o funda con /pais crear)")), true);
        ctx.getSource().sendFeedback(() -> Text.literal("Moneda creada automáticamente: "
                + g.getName() + " [" + g.getTag() + "] (moneda dorada por defecto). "
                + "Cámbiale el nombre con /moneda nombre o el icono con /moneda icono <url>."), false);
        return 1;
    }

    /** Abre la interfaz de creación de país (tema Art Déco) para elegir forma política. */
    private static int openCreate(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        com.realeconomy.politics.CreationActions.openFor(player);
        return 1;
    }

    private static int formShow(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(player.getUuid());
        if (g == null) {
            error(ctx, "No perteneces a ningún país.");
            return 0;
        }
        var def = com.realeconomy.politics.PoliticalForms.get(g.getForm(), g.getSubtype());
        ctx.getSource().sendFeedback(() -> {
            MutableBuilder b = new MutableBuilder();
            b.line(Text.literal("Forma política de ").append(groupLabel(g)).append(Text.literal(": ")
                    .append(Text.literal(com.realeconomy.politics.PoliticalForms.displayName(g.getForm(), g.getSubtype()))
                            .formatted(Formatting.GOLD))));
            if (def.howToPower != null && !def.howToPower.isBlank()) {
                b.line(Text.literal("Cómo se gobierna: " + def.howToPower).formatted(Formatting.GRAY));
            }
            b.line(Text.literal("El líder la cambia con /pais forma <forma> [subtipo].").formatted(Formatting.DARK_GRAY));
            return b.build();
        }, false);
        return 1;
    }

    /** Cambia la forma política (solo el líder). */
    private static int setForm(CommandContext<ServerCommandSource> ctx, String subtypeArg) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede cambiar la forma política del país.");
            return 0;
        }
        com.realeconomy.politics.FormType form =
                com.realeconomy.politics.FormType.parse(StringArgumentType.getString(ctx, "forma"));
        if (form == null) {
            error(ctx, "Forma no válida. Opciones: ninguna, democracia, republica, monarquia_absoluta, "
                    + "monarquia_parlamentaria, dictadura, oligarquia, teocracia, tecnocracia, anarquia.");
            return 0;
        }
        com.realeconomy.politics.FormSubtype subtype = com.realeconomy.politics.FormSubtype.NONE;
        if (form.requiresSubtype()) {
            subtype = subtypeArg == null ? null : com.realeconomy.politics.FormSubtype.parse(subtypeArg);
            if (subtype == null || subtype == com.realeconomy.politics.FormSubtype.NONE || !form.subtypes().contains(subtype)) {
                StringBuilder opts = new StringBuilder();
                for (com.realeconomy.politics.FormSubtype s : form.subtypes()) {
                    if (opts.length() > 0) opts.append(", ");
                    opts.append(s.name().toLowerCase(java.util.Locale.ROOT));
                }
                error(ctx, form.displayName() + " requiere un subtipo: " + opts + ".");
                return 0;
            }
        }
        mgr.setForm(g, form, subtype);
        com.realeconomy.politics.FormType ff = form;
        com.realeconomy.politics.FormSubtype fs = subtype;
        ctx.getSource().sendFeedback(() -> Text.literal("Forma política de ").append(groupLabel(g))
                .append(Text.literal(" cambiada a "
                        + com.realeconomy.politics.PoliticalForms.displayName(ff, fs) + "."))
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int invite(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null) {
            error(ctx, "No perteneces a ningún país.");
            return 0;
        }
        if (!g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede invitar.");
            return 0;
        }

        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "jugador");
        if (mgr.getByPlayer(target.getUuid()) != null) {
            error(ctx, target.getNameForScoreboard() + " ya pertenece a un país.");
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
                .append(Text.literal(". Usa /pais accept " + g.getTag() + " para unirte o /pais deny " + g.getTag() + " para rechazarla.")));
        return 1;
    }

    private static int accept(CommandContext<ServerCommandSource> ctx, String tagArg) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());

        if (mgr.getByPlayer(player.getUuid()) != null) {
            error(ctx, "Ya perteneces a un país.");
            return 0;
        }

        Group g;
        if (tagArg != null) {
            g = mgr.getByTag(tagArg);
            if (g == null || !mgr.hasInvite(player.getUuid(), g)) {
                error(ctx, "No tienes una invitación de ese país.");
                return 0;
            }
        } else {
            var ids = mgr.getInvites(player.getUuid());
            if (ids.isEmpty()) {
                error(ctx, "No tienes invitaciones pendientes.");
                return 0;
            }
            if (ids.size() > 1) {
                error(ctx, "Tienes varias invitaciones. Especifica las siglas: /pais accept <siglas>.");
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
        notifyMembers(ctx.getSource().getServer(), g, Text.literal(player.getNameForScoreboard() + " se ha unido al país."), player.getUuid());
        return 1;
    }

    private static int deny(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        String tag = StringArgumentType.getString(ctx, "siglas");
        Group g = mgr.getByTag(tag);
        if (g == null || !mgr.hasInvite(player.getUuid(), g)) {
            error(ctx, "No tienes una invitación de ese país.");
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
            error(ctx, "No perteneces a ningún país.");
            return 0;
        }
        if (!g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder puede expulsar.");
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "jugador");
        UUID targetUuid = resolveMember(g, name);
        if (targetUuid == null) {
            error(ctx, "Ese jugador no está en tu país.");
            return 0;
        }
        if (targetUuid.equals(player.getUuid())) {
            error(ctx, "No puedes expulsarte a ti mismo. Usa /pais disband para disolver el país.");
            return 0;
        }

        mgr.removeMember(g, targetUuid);
        ctx.getSource().sendFeedback(() -> Text.literal("Has expulsado a " + name + " del país."), false);
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
            error(ctx, "No perteneces a ningún país.");
            return 0;
        }
        if (g.isLeader(player.getUuid())) {
            error(ctx, "Eres el líder. Transfiere el liderazgo (/pais transfer <jugador>) o disuelve el país (/pais disband).");
            return 0;
        }

        mgr.removeMember(g, player.getUuid());
        ctx.getSource().sendFeedback(() -> Text.literal("Has salido de ").append(groupLabel(g)), false);
        notifyMembers(ctx.getSource().getServer(), g, Text.literal(player.getNameForScoreboard() + " ha salido del país."), player.getUuid());
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
            error(ctx, "Ese jugador no está en tu país.");
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
            error(ctx, "Solo el líder puede disolver el país.");
            return 0;
        }
        notifyMembers(ctx.getSource().getServer(), g, Text.literal("Tu país ha sido disuelto por el líder."), player.getUuid());
        mgr.disband(g);
        ctx.getSource().sendFeedback(() -> Text.literal("País disuelto."), true);
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

    /** Designa (add) o destituye (remove) un ministro. Solo el líder. */
    private static int setMinister(CommandContext<ServerCommandSource> ctx, boolean add) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        GroupManager mgr = GroupManager.get(server);

        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder del país puede designar o destituir ministros.");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "jugador");
        UUID target = resolveMember(g, name);
        if (target == null) {
            error(ctx, "Ese jugador no está en tu país.");
            return 0;
        }
        if (target.equals(player.getUuid())) {
            error(ctx, "El líder ya forma parte del gobierno; no puede ser ministro de sí mismo.");
            return 0;
        }
        if (add) {
            if (!mgr.addMinister(g, target)) {
                error(ctx, name + " ya es ministro.");
                return 0;
            }
            ctx.getSource().sendFeedback(() -> Text.literal(name + " es ahora ministro del gobierno de ")
                    .append(groupLabel(g)).append(Text.literal(".")), false);
            ServerPlayerEntity tp = server.getPlayerManager().getPlayer(target);
            if (tp != null) {
                tp.sendMessage(Text.literal("Has sido nombrado ministro de ").append(groupLabel(g))
                        .append(Text.literal(". Ya puedes gestionar la economía del país.")).formatted(Formatting.GREEN));
            }
        } else {
            if (!mgr.removeMinister(g, target)) {
                error(ctx, name + " no es ministro.");
                return 0;
            }
            ctx.getSource().sendFeedback(() -> Text.literal(name + " ya no es ministro."), false);
            ServerPlayerEntity tp = server.getPlayerManager().getPlayer(target);
            if (tp != null) {
                tp.sendMessage(Text.literal("Has sido destituido como ministro de ").append(groupLabel(g))
                        .append(Text.literal(".")));
            }
        }
        return 1;
    }

    /** Monarquía absoluta: el rey (líder) designa a su sucesor. */
    private static int setHeir(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el líder (rey) puede designar sucesor.");
            return 0;
        }
        var mode = com.realeconomy.politics.PoliticalForms.get(g.getForm(), g.getSubtype()).mode();
        if (mode != com.realeconomy.politics.GovernmentMode.MONARCH) {
            error(ctx, "Designar sucesor es propio de una monarquía absoluta. Tu forma es "
                    + com.realeconomy.politics.PoliticalForms.displayName(g.getForm(), g.getSubtype()) + ".");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "jugador");
        UUID target = resolveMember(g, name);
        if (target == null) {
            error(ctx, "Ese jugador no está en tu país.");
            return 0;
        }
        g.setHeir(target);
        mgr.markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal(name + " es ahora el sucesor del trono de ")
                .append(groupLabel(g)).append(Text.literal(". Usa /pais abdicar para cederle la corona.")), true);
        return 1;
    }

    /** Monarquía absoluta: el rey abdica y cede la corona a su sucesor designado. */
    private static int abdicate(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        Group g = mgr.getByPlayer(player.getUuid());
        if (g == null || !g.isLeader(player.getUuid())) {
            error(ctx, "Solo el rey puede abdicar.");
            return 0;
        }
        UUID heir = g.getHeir();
        if (heir == null || !g.isMember(heir)) {
            error(ctx, "No has designado un sucesor válido. Usa /pais sucesor <jugador> primero.");
            return 0;
        }
        String heirName = g.getMemberName(heir);
        mgr.setLeader(g, heir);
        g.setHeir(null);
        mgr.markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Has abdicado. " + heirName + " es el nuevo rey de ")
                .append(groupLabel(g)).append(Text.literal(".")), true);
        return 1;
    }

    /** Lista el gobierno (líder + ministros) del país del jugador. */
    private static int governmentList(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(player.getUuid());
        if (g == null) {
            error(ctx, "No perteneces a ningún país.");
            return 0;
        }
        final Group fg = g;
        final MinecraftServer server = ctx.getSource().getServer();
        ctx.getSource().sendFeedback(() -> {
            MutableBuilder b = new MutableBuilder();
            b.line(Text.literal("=== Gobierno de ").append(groupLabel(fg)).append(Text.literal(" ===")));
            b.line(Text.literal("Forma política: "
                    + com.realeconomy.politics.PoliticalForms.displayName(fg.getForm(), fg.getSubtype()))
                    .formatted(Formatting.GOLD));
            var mode = com.realeconomy.politics.PoliticalForms.get(fg.getForm(), fg.getSubtype()).mode();
            switch (mode) {
                case NONE -> b.line(Text.literal("Anarquía: nadie gobierna. No se puede gestionar la cuenta, impuestos ni licencias.")
                        .formatted(Formatting.GRAY));
                case PLUTOCRACY -> {
                    var rich = com.realeconomy.politics.Governance.richestGovernors(server, fg);
                    if (rich.isEmpty()) {
                        b.line(Text.literal("Plutocracia: gobierna el más rico, pero nadie tiene saldo todavía."));
                    } else {
                        StringBuilder names = new StringBuilder();
                        for (UUID u : rich) {
                            if (names.length() > 0) names.append(", ");
                            names.append(fg.getMemberName(u));
                        }
                        b.line(Text.literal("Gobierno (los más ricos): " + names));
                    }
                }
                case ELECTED_PARTY, PARLIAMENTARY -> {
                    if (mode == com.realeconomy.politics.GovernmentMode.PARLIAMENTARY) {
                        b.line(Text.literal("Rey (jefe de Estado, sin poder económico): " + fg.getMemberName(fg.getLeader())));
                    }
                    String pid = fg.getGoverningPartyId();
                    var party = pid == null ? null : com.realeconomy.party.PartyManager.get(server).getById(pid);
                    if (party == null) {
                        b.line(Text.literal("Sin gobierno electo. Hace falta una elección (domingo o /ecoadmin elecciones).")
                                .formatted(Formatting.GRAY));
                    } else {
                        b.line(Text.literal("Partido en el gobierno: " + party.getName()
                                + " · jefe de gobierno: " + fg.getMemberName(party.getLeader())));
                    }
                }
                case ELECTED_PRESIDENT -> {
                    if (fg.getPresident() == null) {
                        b.line(Text.literal("Sin presidente. Hace falta una elección (domingo o /ecoadmin elecciones).")
                                .formatted(Formatting.GRAY));
                    } else {
                        b.line(Text.literal("Presidente: " + fg.getMemberName(fg.getPresident())));
                    }
                }
                case MONARCH -> {
                    b.line(Text.literal("Rey: " + fg.getMemberName(fg.getLeader())));
                    if (fg.getHeir() != null) {
                        b.line(Text.literal("Sucesor designado: " + fg.getMemberName(fg.getHeir())));
                    }
                }
                default -> b.line(Text.literal("Líder: " + fg.getMemberName(fg.getLeader())));
            }
            // Consejo/junta/clero/ministros designados por el líder (cuando aplica).
            if (mode != com.realeconomy.politics.GovernmentMode.NONE
                    && mode != com.realeconomy.politics.GovernmentMode.PLUTOCRACY) {
                String label = com.realeconomy.politics.Governance.councilLabel(fg);
                if (fg.getMinisters().isEmpty()) {
                    b.line(Text.literal(cap(label) + ": (ninguno). El líder los designa con /pais gobierno add <jugador>."));
                } else {
                    StringBuilder names = new StringBuilder();
                    for (UUID m : fg.getMinisters()) {
                        if (names.length() > 0) names.append(", ");
                        names.append(fg.getMemberName(m));
                    }
                    b.line(Text.literal(cap(label) + " (" + fg.getMinisters().size() + "): " + names));
                }
            }
            b.line(Text.literal("El gobierno gestiona impuestos, licencias, dinero, obras, sueldos y la cuenta del país.")
                    .formatted(Formatting.GRAY));
            return b.build();
        }, false);
        return 1;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int info(CommandContext<ServerCommandSource> ctx, String tagArg) throws CommandSyntaxException {
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        Group g;
        if (tagArg != null) {
            g = mgr.getByTag(tagArg);
            if (g == null) {
                error(ctx, "No existe un país con esas siglas.");
                return 0;
            }
        } else {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            g = mgr.getByPlayer(player.getUuid());
            if (g == null) {
                error(ctx, "No perteneces a ningún país. Usa /pais info <siglas> para ver otro.");
                return 0;
            }
        }

        final Group fg = g;
        ctx.getSource().sendFeedback(() -> {
            MutableBuilder b = new MutableBuilder();
            b.line(Text.literal("=== País ").append(groupLabel(fg)).append(Text.literal(" ===")));
            b.line(Text.literal("Nombre: " + fg.getName()));
            b.line(Text.literal("Color: " + fg.getColor().getName()));
            b.line(Text.literal("Forma política: "
                    + com.realeconomy.politics.PoliticalForms.displayName(fg.getForm(), fg.getSubtype())));
            b.line(Text.literal("Líder: " + fg.getMemberName(fg.getLeader())));
            if (!fg.getMinisters().isEmpty()) {
                StringBuilder mins = new StringBuilder();
                for (UUID m : fg.getMinisters()) {
                    if (mins.length() > 0) mins.append(", ");
                    mins.append(fg.getMemberName(m));
                }
                b.line(Text.literal("Ministros (" + fg.getMinisters().size() + "): " + mins));
            }
            b.line(Text.literal("Miembros (" + fg.size() + "): " + String.join(", ", fg.getMemberNames().values())));
            var parties = com.realeconomy.party.PartyManager.get(ctx.getSource().getServer()).partiesOf(fg.getId());
            if (!parties.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (var p : parties) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(p.getName()).append(" (").append(p.size()).append(")");
                }
                b.line(Text.literal("Partidos (" + parties.size() + "): " + names));
            }
            return b.build();
        }, false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        GroupManager mgr = GroupManager.get(ctx.getSource().getServer());
        var groups = mgr.getGroups();
        if (groups.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("No hay países todavía."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> {
            MutableBuilder b = new MutableBuilder();
            b.line(Text.literal("=== Países (" + groups.size() + ") ==="));
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
