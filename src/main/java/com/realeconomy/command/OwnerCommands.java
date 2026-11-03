package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.attitude.AttitudeManager;
import com.realeconomy.attitude.AttitudeMode;
import com.realeconomy.attitude.OwnerActions;
import com.realeconomy.compat.ModCompat;
import com.realeconomy.compat.mca.Mca;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /aldeano (alias /mcavillager): el dueño (familia jugadora) gestiona el aldeano de
 * MCA más cercano del que es familia: abrir su GUI de equipo/modo o fijar el modo por comando.
 */
public final class OwnerCommands {

    private OwnerCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("aldeano")
                .executes(OwnerCommands::open)
                .then(literal("modo")
                        .then(argument("modo", StringArgumentType.word())
                                .executes(OwnerCommands::setMode)))
                .then(literal("info").executes(OwnerCommands::open)));
        dispatcher.register(literal("mcavillager").redirect(node));
    }

    /** Aldeano de MCA más cercano del que el jugador es dueño (familia), o null (con aviso). */
    private static VillagerEntity nearestOwned(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity p)
            throws CommandSyntaxException {
        if (!ModCompat.mcaEnabled()) {
            ctx.getSource().sendError(Text.literal("MCA Reborn no está instalado/activo."));
            return null;
        }
        ServerWorld world = p.getServerWorld();
        Box box = p.getBoundingBox().expand(8.0);
        List<VillagerEntity> near = world.getEntitiesByClass(VillagerEntity.class, box,
                e -> Mca.isMcaVillager(e) && Mca.isOwner(e, p.getUuid()));
        near.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));
        if (near.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Acércate a un aldeano de MCA del que seas familia."));
            return null;
        }
        return near.get(0);
    }

    private static int open(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        VillagerEntity v = nearestOwned(ctx, p);
        if (v == null) {
            return 0;
        }
        OwnerActions.openFor(p, v);
        return 1;
    }

    private static int setMode(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        VillagerEntity v = nearestOwned(ctx, p);
        if (v == null) {
            return 0;
        }
        AttitudeMode mode = AttitudeMode.parse(StringArgumentType.getString(ctx, "modo"));
        if (mode == null) {
            ctx.getSource().sendError(Text.literal("Modo no válido: pasivo, neutral o agresivo."));
            return 0;
        }
        AttitudeManager.get(ctx.getSource().getServer()).set(v.getUuid(), mode);
        ctx.getSource().sendFeedback(() -> Text.literal("Modo del aldeano: " + mode.display() + ".")
                .formatted(Formatting.GOLD), false);
        return 1;
    }
}
