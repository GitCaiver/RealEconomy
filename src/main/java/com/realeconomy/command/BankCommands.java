package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.bank.BankEntities;
import com.realeconomy.config.ModConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

/** Comandos para crear/borrar el NPC del banco (solo op). */
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
