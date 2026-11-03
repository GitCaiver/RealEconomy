package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.config.ModConfig;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.politics.Governance;
import com.realeconomy.territory.Claim;
import com.realeconomy.territory.Province;
import com.realeconomy.territory.TerritoryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /territorio (alias /territory): el gobierno de un país reclama o abandona
 * chunks para su país (opcionalmente dentro de una provincia). Las vistas (info/mapa/
 * lista) están abiertas a cualquiera.
 */
public final class TerritoryCommands {

    private TerritoryCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("territorio")
                .then(literal("reclamar")
                        .executes(ctx -> claim(ctx, null))
                        .then(argument("provincia", StringArgumentType.greedyString())
                                .executes(ctx -> claim(ctx, StringArgumentType.getString(ctx, "provincia")))))
                .then(literal("abandonar").executes(TerritoryCommands::unclaim))
                .then(literal("info").executes(TerritoryCommands::info))
                .then(literal("mapa").executes(TerritoryCommands::map))
                .then(literal("lista").executes(TerritoryCommands::list)));
        dispatcher.register(literal("territory").redirect(node));
    }

    /** Devuelve el país del jugador si es gobierno; si no, manda error y devuelve null. */
    private static Group governingCountry(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        Group g = GroupManager.get(server).getByPlayer(p.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return null;
        }
        if (!Governance.isGovernment(server, g, p.getUuid())) {
            ctx.getSource().sendError(Text.literal(Governance.notGovernmentMessage(server, g)));
            return null;
        }
        return g;
    }

    private static int claim(CommandContext<ServerCommandSource> ctx, String provinceName) throws CommandSyntaxException {
        Group g = governingCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        ServerWorld world = p.getServerWorld();
        ChunkPos cp = new ChunkPos(p.getBlockPos());
        TerritoryManager tm = TerritoryManager.get(server);

        Claim existing = tm.getClaim(world, cp);
        if (existing != null) {
            if (existing.countryId().equals(g.getId())) {
                ctx.getSource().sendError(Text.literal("Este chunk ya es de tu país."));
            } else {
                Group owner = GroupManager.get(server).getById(existing.countryId());
                ctx.getSource().sendError(Text.literal("Este chunk ya pertenece a "
                        + (owner != null ? owner.getName() : "otro país") + " (consíguelo en una guerra)."));
            }
            return 0;
        }

        int max = ModConfig.get().maxClaimsPerCountry;
        if (max >= 0 && tm.claimCount(g.getId()) >= max) {
            ctx.getSource().sendError(Text.literal("Tu país ya tiene el máximo de chunks reclamados (" + max + ")."));
            return 0;
        }

        String provinceId = null;
        String provinceLabel = "";
        if (provinceName != null && !provinceName.isBlank()) {
            Province prov = tm.getProvinceByName(g.getId(), provinceName.trim());
            if (prov == null) {
                ctx.getSource().sendError(Text.literal("Tu país no tiene una provincia llamada \""
                        + provinceName.trim() + "\". Créala con /provincia crear."));
                return 0;
            }
            provinceId = prov.getId();
            provinceLabel = " (provincia " + prov.getName() + ")";
        }

        tm.claim(world, cp, g.getId(), provinceId);
        final String lbl = provinceLabel;
        ctx.getSource().sendFeedback(() -> Text.literal("Chunk [" + cp.x + ", " + cp.z + "] reclamado para "
                + g.getName() + lbl + ".").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int unclaim(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = governingCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        ServerWorld world = p.getServerWorld();
        ChunkPos cp = new ChunkPos(p.getBlockPos());
        TerritoryManager tm = TerritoryManager.get(server);
        Claim c = tm.getClaim(world, cp);
        if (c == null || !c.countryId().equals(g.getId())) {
            ctx.getSource().sendError(Text.literal("Este chunk no es de tu país."));
            return 0;
        }
        tm.unclaim(world, cp);
        ctx.getSource().sendFeedback(() -> Text.literal("Chunk [" + cp.x + ", " + cp.z + "] abandonado.")
                .formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        ServerWorld world = p.getServerWorld();
        ChunkPos cp = new ChunkPos(p.getBlockPos());
        TerritoryManager tm = TerritoryManager.get(server);
        Claim c = tm.getClaim(world, cp);
        if (c == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("Chunk [" + cp.x + ", " + cp.z + "]: sin dueño.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }
        Group owner = GroupManager.get(server).getById(c.countryId());
        Province prov = tm.getProvince(c.provinceId());
        MutableText t = Text.literal("Chunk [" + cp.x + ", " + cp.z + "]: ")
                .append(Text.literal(owner != null ? owner.getName() + " [" + owner.getTag() + "]" : "país desconocido")
                        .formatted(owner != null ? owner.getColor() : Formatting.WHITE));
        if (prov != null) {
            t.append(Text.literal(" · provincia " + prov.getName()).formatted(prov.getColor()));
        }
        ctx.getSource().sendFeedback(() -> t, false);
        return 1;
    }

    private static int map(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        ServerWorld world = p.getServerWorld();
        ChunkPos center = new ChunkPos(p.getBlockPos());
        TerritoryManager tm = TerritoryManager.get(server);
        Group own = GroupManager.get(server).getByPlayer(p.getUuid());
        String ownId = own == null ? null : own.getId();

        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Mapa de territorios (tú = centro):");
            for (int dz = -4; dz <= 4; dz++) {
                MutableText row = Text.literal("\n");
                for (int dx = -6; dx <= 6; dx++) {
                    ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                    Claim c = tm.getClaim(world, cp);
                    boolean here = dx == 0 && dz == 0;
                    String glyph = here ? "¤" : (c == null ? "·" : "■");
                    Formatting col;
                    if (c == null) {
                        col = Formatting.DARK_GRAY;
                    } else {
                        Group g = GroupManager.get(server).getById(c.countryId());
                        col = g != null ? g.getColor() : Formatting.WHITE;
                    }
                    if (here) {
                        col = Formatting.WHITE;
                    }
                    row.append(Text.literal(glyph).formatted(col));
                }
                t.append(row);
            }
            if (ownId != null) {
                t.append(Text.literal("\n■ = reclamado (color del país) · · = sin dueño").formatted(Formatting.GRAY));
            }
            return t;
        }, false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(server).getByPlayer(p.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return 0;
        }
        TerritoryManager tm = TerritoryManager.get(server);
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Territorio de " + g.getName() + " [" + g.getTag() + "]: "
                    + tm.claimCount(g.getId()) + " chunk(s).").formatted(g.getColor());
            for (Province prov : tm.provincesOf(g.getId())) {
                t.append(Text.literal("\n - Provincia " + prov.getName() + ": "
                        + tm.claimCountOfProvince(prov.getId()) + " chunk(s)").formatted(prov.getColor()));
            }
            return t;
        }, false);
        return 1;
    }
}
