package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.config.ModConfig;
import com.realeconomy.townhall.TownhallEntities;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

/** Comandos para crear/borrar el NPC del ayuntamiento (solo op). */
public final class TownhallCommands {

    private TownhallCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("ayuntamientocrear")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(TownhallCommands::create));

        dispatcher.register(literal("ayuntamientoborrar")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(TownhallCommands::remove));
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        TownhallEntities.spawn(player);
        ctx.getSource().sendFeedback(() -> Text.literal("Ayuntamiento creado aquí. Haz clic derecho para usarlo."), true);
        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        double range = Math.max(8.0, ModConfig.get().townhallRange);
        boolean removed = TownhallEntities.removeNearest(player.getServerWorld(), player.getPos(), range);
        if (removed) {
            ctx.getSource().sendFeedback(() -> Text.literal("Ayuntamiento eliminado."), true);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("No hay ningún ayuntamiento cerca."));
        return 0;
    }
}
