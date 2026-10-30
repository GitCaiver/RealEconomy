package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.jobs.Job;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /trabajo (alias /jobs): ver los sueldos por trabajo del grupo y, el líder,
 * fijarlos. Al realizar la actividad, el miembro cobra desde la cuenta del grupo.
 */
public final class JobCommands {

    private JobCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("trabajo")
                .then(literal("sueldos").executes(JobCommands::list))
                .then(literal("info").executes(JobCommands::list))
                .then(literal("sueldo")
                        .then(argument("trabajo", StringArgumentType.word())
                                .then(argument("cantidad", LongArgumentType.longArg(0))
                                        .executes(JobCommands::setWage))));

        var node = dispatcher.register(root);
        dispatcher.register(literal("jobs").redirect(node));
    }

    private static Group playerGroup(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún grupo."));
        }
        return g;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        FinanceManager fm = FinanceManager.get(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Sueldos por trabajo de ")
                    .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor())).append(":");
            for (Job job : Job.values()) {
                long wage = fm.getWage(g.getId(), job);
                String val = wage > 0 ? (wage + " / acción") : "(desactivado)";
                t.append(Text.literal("\n - " + job.getKey() + " — " + job.getDescription() + ": " + val));
            }
            t.append(Text.literal("\nEl pago sale de la cuenta del grupo (/tesoreria).").formatted(net.minecraft.util.Formatting.DARK_GRAY));
            return t;
        }, false);
        return 1;
    }

    private static int setWage(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerGroup(ctx);
        if (g == null) {
            return 0;
        }
        if (!g.isLeader(ctx.getSource().getPlayerOrThrow().getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el líder puede fijar los sueldos."));
            return 0;
        }
        String key = StringArgumentType.getString(ctx, "trabajo");
        Job job = Job.fromKey(key);
        if (job == null) {
            ctx.getSource().sendError(Text.literal("Trabajo no válido. Opciones: " + Job.keysList() + "."));
            return 0;
        }
        long amount = LongArgumentType.getLong(ctx, "cantidad");
        FinanceManager.get(ctx.getSource().getServer()).setWage(g.getId(), job, amount);
        ctx.getSource().sendFeedback(() -> Text.literal(amount > 0
                ? ("Sueldo de " + job.getKey() + " fijado en " + amount + " por acción.")
                : ("Trabajo " + job.getKey() + " desactivado.")), true);
        return 1;
    }
}
