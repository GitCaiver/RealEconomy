package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.config.ModConfig;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.politics.Governance;
import com.realeconomy.territory.Province;
import com.realeconomy.territory.TerritoryManager;
import com.realeconomy.util.TimeUtil;
import com.realeconomy.war.War;
import com.realeconomy.war.WarManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /guerra (alias /war): el gobierno declara guerra a una provincia de otro
 * país, los miembros se apuntan, y se decide por eliminación, rendición o tiempo.
 */
public final class WarCommands {

    private WarCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var node = dispatcher.register(literal("guerra")
                .executes(WarCommands::status)
                .then(literal("estado").executes(WarCommands::status))
                .then(literal("lista").executes(WarCommands::listAll))
                .then(literal("declarar")
                        .then(argument("defensor", StringArgumentType.word())
                                .then(argument("provincia", StringArgumentType.word())
                                        .executes(ctx -> declare(ctx, null))
                                        .then(argument("duracion", StringArgumentType.word())
                                                .executes(ctx -> declare(ctx,
                                                        StringArgumentType.getString(ctx, "duracion")))))))
                .then(literal("unirse").executes(WarCommands::join))
                .then(literal("alistar").executes(WarCommands::enlistVillagers))
                .then(literal("rendirse").executes(WarCommands::surrender))
                .then(literal("cancelar").executes(WarCommands::cancel))
                .then(literal("historial").executes(WarCommands::historyCmd)));
        dispatcher.register(literal("war").redirect(node));
    }

    private static Group playerCountry(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(p.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
        }
        return g;
    }

    private static int declare(CommandContext<ServerCommandSource> ctx, String durationText) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group attacker = playerCountry(ctx);
        if (attacker == null) {
            return 0;
        }
        if (!ModConfig.get().serverLaws.allowWar) {
            ctx.getSource().sendError(Text.literal("El servidor tiene las guerras desactivadas."));
            return 0;
        }
        if (!Governance.isGovernment(server, attacker, p.getUuid())) {
            ctx.getSource().sendError(Text.literal(Governance.notGovernmentMessage(server, attacker)));
            return 0;
        }
        if (!com.realeconomy.politics.LawEnforcement.allowsArmy(attacker)) {
            ctx.getSource().sendError(Text.literal("La forma política de tu país prohíbe los ejércitos y la guerra."));
            return 0;
        }

        GroupManager gm = GroupManager.get(server);
        Group defender = gm.getByTag(StringArgumentType.getString(ctx, "defensor"));
        if (defender == null) {
            ctx.getSource().sendError(Text.literal("No existe un país con esas siglas."));
            return 0;
        }
        if (defender.getId().equals(attacker.getId())) {
            ctx.getSource().sendError(Text.literal("No puedes declararte la guerra a ti mismo."));
            return 0;
        }

        TerritoryManager tm = TerritoryManager.get(server);
        Province prov = tm.getProvinceByName(defender.getId(), StringArgumentType.getString(ctx, "provincia"));
        if (prov == null) {
            ctx.getSource().sendError(Text.literal(defender.getName() + " no tiene una provincia con ese nombre."));
            return 0;
        }
        if (tm.claimCountOfProvince(prov.getId()) == 0) {
            ctx.getSource().sendError(Text.literal("Esa provincia no tiene territorio (sin chunks que conquistar)."));
            return 0;
        }
        if (WarManager.get(server).existsDuplicate(attacker.getId(), defender.getId(), prov.getId())) {
            ctx.getSource().sendError(Text.literal("Ya hay una guerra activa por esa provincia."));
            return 0;
        }

        long dur = TimeUtil.parsePeriod(durationText != null ? durationText : ModConfig.get().warDefaultDuration);
        long min = TimeUtil.parsePeriod(ModConfig.get().warMinDuration);
        if (dur < 0) {
            ctx.getSource().sendError(Text.literal("Duración no válida (usa 30s, 10m, 2h, 1d)."));
            return 0;
        }
        if (min > 0 && dur < min) {
            dur = min;
        }

        WarManager.get(server).declare(server, attacker, defender, prov.getId(), dur);
        ctx.getSource().sendFeedback(() -> Text.literal("Guerra declarada contra " + defender.getName()
                + " por la provincia " + prov.getName() + ".").formatted(Formatting.RED), false);
        return 1;
    }

    private static int enlistVillagers(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = playerCountry(ctx);
        if (g == null) {
            return 0;
        }
        if (!com.realeconomy.compat.ModCompat.mcaEnabled()) {
            ctx.getSource().sendError(Text.literal("MCA Reborn no está instalado/activo."));
            return 0;
        }
        if (!com.realeconomy.politics.LawEnforcement.allowsArmy(g)) {
            ctx.getSource().sendError(Text.literal("La forma política de tu país prohíbe los ejércitos."));
            return 0;
        }
        var wars = WarManager.get(server).warsInvolving(g.getId());
        if (wars.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Tu país no está en ninguna guerra."));
            return 0;
        }
        War w = wars.get(0);
        int side = w.sideOfCountry(g.getId());
        int maxPlayer = com.realeconomy.config.ModConfig.get().maxVillagersPerPlayerInWar;
        int maxCountry = com.realeconomy.config.ModConfig.get().maxVillagersPerCountryInWar;

        var box = p.getBoundingBox().expand(12.0);
        var owned = p.getServerWorld().getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class, box,
                e -> com.realeconomy.compat.mca.Mca.isMcaVillager(e)
                        && com.realeconomy.compat.mca.Mca.isOwner(e, p.getUuid())
                        && w.villagerSide(e.getUuid()) == War.SIDE_NONE);
        int enlisted = 0;
        for (var v : owned) {
            if (maxPlayer >= 0 && w.villagerCountForOwner(side, p.getUuid()) >= maxPlayer) {
                break;
            }
            if (maxCountry >= 0 && w.villagerCount(side) >= maxCountry) {
                break;
            }
            w.enlistVillager(side, v.getUuid(), p.getUuid());
            enlisted++;
        }
        WarManager.get(server).markDirty();
        final int n = enlisted;
        if (n == 0) {
            ctx.getSource().sendError(Text.literal("No se alistó ningún aldeano (¿ninguno tuyo cerca o límite alcanzado?)."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Alistaste " + n + " aldeano(s) en la guerra. "
                + "Ponlos en Agresivo para que luchen en la zona.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int join(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = playerCountry(ctx);
        if (g == null) {
            return 0;
        }
        var wars = WarManager.get(server).warsInvolving(g.getId());
        if (wars.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Tu país no está en ninguna guerra."));
            return 0;
        }
        int joined = 0;
        for (War w : wars) {
            int side = w.sideOfCountry(g.getId());
            var set = side == War.SIDE_ATTACKER ? w.attackers() : w.defenders();
            if (set.add(p.getUuid())) {
                joined++;
            }
            w.eliminated().remove(p.getUuid());
        }
        WarManager.get(server).markDirty();
        final int n = joined;
        ctx.getSource().sendFeedback(() -> Text.literal(n > 0
                ? "Te has unido al esfuerzo de guerra de tu país."
                : "Ya estabas apuntado a la(s) guerra(s) de tu país.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int surrender(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return concede(ctx, true);
    }

    private static int cancel(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return concede(ctx, false);
    }

    /** defenderConcedes=true → rendirse (gana atacante); false → cancelar (gana defensor). */
    private static int concede(CommandContext<ServerCommandSource> ctx, boolean defenderConcedes) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = playerCountry(ctx);
        if (g == null) {
            return 0;
        }
        if (!Governance.isGovernment(server, g, p.getUuid())) {
            ctx.getSource().sendError(Text.literal(Governance.notGovernmentMessage(server, g)));
            return 0;
        }
        WarManager wm = WarManager.get(server);
        War target = null;
        for (War w : wm.warsInvolving(g.getId())) {
            int side = w.sideOfCountry(g.getId());
            if (defenderConcedes && side == War.SIDE_DEFENDER) {
                target = w;
                break;
            }
            if (!defenderConcedes && side == War.SIDE_ATTACKER) {
                target = w;
                break;
            }
        }
        if (target == null) {
            ctx.getSource().sendError(Text.literal(defenderConcedes
                    ? "Tu país no está defendiéndose en ninguna guerra."
                    : "Tu país no está atacando en ninguna guerra."));
            return 0;
        }
        if (defenderConcedes) {
            wm.surrender(server, target);
        } else {
            wm.cancel(server, target);
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Guerra terminada.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(server).getByPlayer(p.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
            return 0;
        }
        var wars = WarManager.get(server).warsInvolving(g.getId());
        ctx.getSource().sendFeedback(() -> {
            if (wars.isEmpty()) {
                return Text.literal("Tu país no está en guerra.").formatted(Formatting.GRAY);
            }
            MutableText t = Text.literal("Guerras de " + g.getName() + ":").formatted(g.getColor());
            for (War w : wars) {
                t.append(warLine(server, w));
            }
            return t;
        }, false);
        return 1;
    }

    private static int listAll(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        var wars = WarManager.get(server).activeWars();
        ctx.getSource().sendFeedback(() -> {
            if (wars.isEmpty()) {
                return Text.literal("No hay ninguna guerra activa.").formatted(Formatting.GRAY);
            }
            MutableText t = Text.literal("Guerras activas:");
            for (War w : wars) {
                t.append(warLine(server, w));
            }
            return t;
        }, false);
        return 1;
    }

    private static int historyCmd(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        var hist = WarManager.get(server).history();
        GroupManager gm = GroupManager.get(server);
        ctx.getSource().sendFeedback(() -> {
            if (hist.isEmpty()) {
                return Text.literal("Sin guerras en el historial.").formatted(Formatting.GRAY);
            }
            MutableText t = Text.literal("Historial de guerras:");
            for (War w : hist) {
                Group a = gm.getById(w.getAttackerId());
                Group d = gm.getById(w.getDefenderId());
                Group win = w.getWinnerId() == null || w.getWinnerId().isEmpty() ? null : gm.getById(w.getWinnerId());
                t.append(Text.literal("\n - " + tag(a) + " vs " + tag(d) + " → "
                        + (win != null ? "ganó " + tag(win) : "sin vencedor")).formatted(Formatting.GRAY));
            }
            return t;
        }, false);
        return 1;
    }

    private static MutableText warLine(MinecraftServer server, War w) {
        GroupManager gm = GroupManager.get(server);
        Group a = gm.getById(w.getAttackerId());
        Group d = gm.getById(w.getDefenderId());
        Province prov = TerritoryManager.get(server).getProvince(w.getProvinceId());
        return Text.literal("\n - " + tag(a) + " ⚔ " + tag(d)
                + " · provincia " + (prov != null ? prov.getName() : "?")
                + " · vivos " + w.aliveCount(War.SIDE_ATTACKER) + " vs " + w.aliveCount(War.SIDE_DEFENDER)
                + " · aldeanos " + w.villagerCount(War.SIDE_ATTACKER) + " vs " + w.villagerCount(War.SIDE_DEFENDER)
                + " · quedan " + TimeUtil.format(w.remaining())).formatted(Formatting.WHITE);
    }

    private static String tag(Group g) {
        return g != null ? "[" + g.getTag() + "]" : "[?]";
    }
}
