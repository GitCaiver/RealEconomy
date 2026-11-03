package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.compat.ModCompat;
import com.realeconomy.compat.mca.Mca;
import com.realeconomy.config.ModConfig;
import com.realeconomy.employment.EmployedVillager;
import com.realeconomy.employment.EmploymentManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.politics.Governance;
import com.realeconomy.util.TimeUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /empleo (alias /jobsmca): gestionar a los aldeanos de MCA empleados (zona de
 * trabajo, cofre vinculado, despedir, listar). Contratar y fijar sueldo se hace en la GUI
 * (agáchate y clic derecho sobre el aldeano).
 */
public final class EmploymentCommands {

    private EmploymentCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("empleo")
                .then(literal("info").executes(EmploymentCommands::info))
                .then(literal("lista").executes(EmploymentCommands::list)
                        )
                .then(literal("despedir").executes(EmploymentCommands::fire))
                .then(literal("cofre").executes(EmploymentCommands::linkChest))
                .then(literal("zona")
                        .then(argument("radio", IntegerArgumentType.integer(1))
                                .executes(EmploymentCommands::setZone))));
        dispatcher.register(literal("jobsmca").redirect(node));
    }

    /** Aldeano de MCA empleado más cercano que el jugador puede gestionar, o null (con aviso). */
    private static EmployedVillager nearestManaged(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity p)
            throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        if (!ModCompat.mcaEnabled()) {
            ctx.getSource().sendError(Text.literal("MCA Reborn no está instalado/activo."));
            return null;
        }
        EmploymentManager mgr = EmploymentManager.get(server);
        ServerWorld world = p.getServerWorld();
        Box box = p.getBoundingBox().expand(8.0);
        List<VillagerEntity> near = world.getEntitiesByClass(VillagerEntity.class, box,
                e -> Mca.isMcaVillager(e) && mgr.isEmployed(e.getUuid()));
        near.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));
        for (VillagerEntity v : near) {
            EmployedVillager ev = mgr.get(v.getUuid());
            if (ev != null && canManage(server, p, ev)) {
                return ev;
            }
        }
        ctx.getSource().sendError(Text.literal("Acércate a un aldeano de MCA que emplees."));
        return null;
    }

    private static boolean canManage(MinecraftServer server, ServerPlayerEntity p, EmployedVillager ev) {
        if (ev.employer().equals(p.getUuid())) {
            return true;
        }
        Group g = GroupManager.get(server).getById(ev.countryId());
        return g != null && Governance.isGovernment(server, g, p.getUuid());
    }

    private static int info(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        EmployedVillager ev = nearestManaged(ctx, p);
        if (ev == null) {
            return 0;
        }
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Aldeano empleado:").formatted(Formatting.GOLD);
            t.append(Text.literal("\n - Trabajo: " + ev.job().display()));
            t.append(Text.literal("\n - Sueldo: " + ev.wage() + " cada " + TimeUtil.format(ev.periodMs())
                    + (ev.publicHire() ? " (paga el país)" : " (pagas tú)")));
            t.append(Text.literal("\n - Estado: " + (ev.active() ? "trabajando" : "parado (sin fondos)")));
            t.append(Text.literal("\n - Cartera: " + ev.wallet()));
            t.append(Text.literal("\n - Zona: " + (ev.hasZone()
                    ? "radio " + ev.zoneRadius() + " en " + ev.zoneCenter().toShortString() : "sin asignar")));
            t.append(Text.literal("\n - Cofre: " + (ev.chest() != null ? ev.chest().toShortString() : "sin vincular")));
            return t;
        }, false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        if (!ModCompat.mcaEnabled()) {
            ctx.getSource().sendError(Text.literal("MCA Reborn no está instalado/activo."));
            return 0;
        }
        List<EmployedVillager> mine = EmploymentManager.get(server).byEmployer(p.getUuid());
        ctx.getSource().sendFeedback(() -> {
            if (mine.isEmpty()) {
                return Text.literal("No empleas a ningún aldeano.").formatted(Formatting.GRAY);
            }
            MutableText t = Text.literal("Tus aldeanos empleados (" + mine.size() + "):");
            for (EmployedVillager ev : mine) {
                t.append(Text.literal("\n - " + ev.job().display() + ", " + ev.wage() + "/"
                        + TimeUtil.format(ev.periodMs()) + (ev.active() ? "" : " [parado]")));
            }
            return t;
        }, false);
        return 1;
    }

    private static int fire(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        EmployedVillager ev = nearestManaged(ctx, p);
        if (ev == null) {
            return 0;
        }
        EmploymentManager.get(ctx.getSource().getServer()).remove(ev.villager());
        com.realeconomy.employment.LibrarianManager.get(ctx.getSource().getServer()).remove(ev.villager());
        ctx.getSource().sendFeedback(() -> Text.literal("Aldeano despedido.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int setZone(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        EmployedVillager ev = nearestManaged(ctx, p);
        if (ev == null) {
            return 0;
        }
        int radius = Math.min(IntegerArgumentType.getInteger(ctx, "radio"), ModConfig.get().villagerWorkZoneMaxRadius);
        String dim = p.getServerWorld().getRegistryKey().getValue().toString();
        ev.setZone(dim, p.getBlockPos(), radius);
        EmploymentManager.get(ctx.getSource().getServer()).markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Zona de trabajo fijada aquí con radio " + radius
                + ". El aldeano trabajará dentro de ella.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int linkChest(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        EmployedVillager ev = nearestManaged(ctx, p);
        if (ev == null) {
            return 0;
        }
        HitResult hit = p.raycast(6.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendError(Text.literal("Mira a un cofre para vincularlo."));
            return 0;
        }
        BlockPos pos = bhr.getBlockPos();
        if (!(p.getServerWorld().getBlockEntity(pos) instanceof Inventory)) {
            ctx.getSource().sendError(Text.literal("Ese bloque no es un contenedor (cofre/barril)."));
            return 0;
        }
        ev.setChest(pos.toImmutable());
        EmploymentManager.get(ctx.getSource().getServer()).markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Cofre vinculado en " + pos.toShortString()
                + ". El aldeano dejará ahí lo que produzca.").formatted(Formatting.GREEN), false);
        return 1;
    }
}
