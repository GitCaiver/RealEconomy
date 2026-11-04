package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.politics.Governance;
import com.realeconomy.territory.Province;
import com.realeconomy.territory.TerritoryManager;
import com.realeconomy.util.ColorUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /provincia (alias /province): el gobierno crea/borra provincias de su país.
 * Los chunks se asignan a una provincia con /territorio reclamar &lt;provincia&gt;.
 */
public final class ProvinceCommands {

    private ProvinceCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("provincia")
                .then(literal("crear")
                        .then(argument("nombre", StringArgumentType.word())
                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "nombre"), null))
                                .then(argument("color", StringArgumentType.word())
                                        .executes(ctx -> create(ctx,
                                                StringArgumentType.getString(ctx, "nombre"),
                                                StringArgumentType.getString(ctx, "color"))))))
                .then(literal("borrar")
                        .then(argument("nombre", StringArgumentType.greedyString())
                                .executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "nombre")))))
                .then(literal("color")
                        .then(argument("nombre", StringArgumentType.word())
                                .then(argument("color", StringArgumentType.word())
                                        .executes(ctx -> color(ctx,
                                                StringArgumentType.getString(ctx, "nombre"),
                                                StringArgumentType.getString(ctx, "color"))))))
                .then(literal("lista").executes(ProvinceCommands::list)));
        dispatcher.register(literal("province").redirect(node));
    }

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

    private static int create(CommandContext<ServerCommandSource> ctx, String name, String colorName) throws CommandSyntaxException {
        Group g = governingCountry(ctx);
        if (g == null) {
            return 0;
        }
        Formatting color = colorName == null ? g.getColor() : ColorUtil.parse(colorName);
        if (color == null) {
            ctx.getSource().sendError(Text.literal("Color no válido. Opciones: " + ColorUtil.displayNames()));
            return 0;
        }
        TerritoryManager tm = TerritoryManager.get(ctx.getSource().getServer());
        Province p = tm.createProvince(g.getId(), name, color);
        if (p == null) {
            ctx.getSource().sendError(Text.literal("Tu país ya tiene una provincia llamada \"" + name + "\"."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Provincia \"" + name + "\" creada. Reclámale chunks con "
                + "/territorio reclamar " + name + ".").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int delete(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
        Group g = governingCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        TerritoryManager tm = TerritoryManager.get(server);
        Province p = tm.getProvinceByName(g.getId(), name.trim());
        if (p == null) {
            ctx.getSource().sendError(Text.literal("Tu país no tiene esa provincia."));
            return 0;
        }
        // Si la provincia tenía un núcleo, retira también su bloque del mundo.
        com.realeconomy.territory.TerritoryCore core = tm.coreOfProvince(p.getId());
        tm.removeProvince(p.getId());
        if (core != null) {
            net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimKey =
                    net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,
                            net.minecraft.util.Identifier.of(core.dim()));
            net.minecraft.server.world.ServerWorld w = server.getWorld(dimKey);
            if (w != null) {
                w.setBlockState(core.pos(), net.minecraft.block.Blocks.AIR.getDefaultState());
            }
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Provincia \"" + p.getName()
                + "\" borrada; sus chunks quedan en el país sin provincia.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int color(CommandContext<ServerCommandSource> ctx, String name, String colorName) throws CommandSyntaxException {
        Group g = governingCountry(ctx);
        if (g == null) {
            return 0;
        }
        Formatting color = ColorUtil.parse(colorName);
        if (color == null) {
            ctx.getSource().sendError(Text.literal("Color no válido. Opciones: " + ColorUtil.displayNames()));
            return 0;
        }
        TerritoryManager tm = TerritoryManager.get(ctx.getSource().getServer());
        Province p = tm.getProvinceByName(g.getId(), name.trim());
        if (p == null) {
            ctx.getSource().sendError(Text.literal("Tu país no tiene esa provincia."));
            return 0;
        }
        p.setColor(color);
        tm.markDirty();
        ctx.getSource().sendFeedback(() -> Text.literal("Color de la provincia \"" + p.getName() + "\" cambiado.")
                .formatted(color), false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = ctx.getSource().getServer();
        Group g = GroupManager.get(server).getByPlayer(p.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return 0;
        }
        TerritoryManager tm = TerritoryManager.get(server);
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Provincias de " + g.getName() + ":").formatted(g.getColor());
            var list = tm.provincesOf(g.getId());
            if (list.isEmpty()) {
                t.append(Text.literal("\n (ninguna)").formatted(Formatting.GRAY));
            }
            for (Province prov : list) {
                t.append(Text.literal("\n - " + prov.getName() + " (" + tm.claimCountOfProvince(prov.getId()) + " chunk(s))")
                        .formatted(prov.getColor()));
            }
            return t;
        }, false);
        return 1;
    }
}
