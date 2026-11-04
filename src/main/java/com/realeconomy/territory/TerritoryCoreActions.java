package com.realeconomy.territory;

import com.realeconomy.config.ModConfig;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.politics.Governance;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Coloca un núcleo territorial (baluarte): crea una provincia nueva, reclama el área
 * a su alrededor y planta el bloque del núcleo. Solo el gobierno de un país puede hacerlo,
 * y solo en terreno sin dueño o propio. Lo invoca el item núcleo ({@code NucleoItem}).
 */
public final class TerritoryCoreActions {

    private TerritoryCoreActions() {
    }

    /** Intenta colocar un núcleo en {@code pos}. Devuelve SUCCESS si se colocó, FAIL si no. */
    public static ActionResult place(ServerPlayerEntity player, ServerWorld world, BlockPos pos, ItemStack stack) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.territoryCoreEnabled) {
            error(player, "Los núcleos territoriales están desactivados en este servidor.");
            return ActionResult.FAIL;
        }
        MinecraftServer server = world.getServer();
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null) {
            error(player, "No perteneces a ningún país.");
            return ActionResult.FAIL;
        }
        if (!Governance.isGovernment(server, g, player.getUuid())) {
            error(player, Governance.notGovernmentMessage(server, g));
            return ActionResult.FAIL;
        }
        if (!world.getBlockState(pos).isReplaceable()) {
            error(player, "Apunta a un sitio despejado para plantar el núcleo.");
            return ActionResult.FAIL;
        }

        TerritoryManager tm = TerritoryManager.get(server);
        String owner = tm.ownerCountry(world, pos);
        if (owner != null && !owner.equals(g.getId())) {
            error(player, "Aquí manda otro país; solo puedes colocar un núcleo en terreno libre o propio.");
            return ActionResult.FAIL;
        }

        int max = cfg.maxClaimsPerCountry;
        if (max >= 0 && tm.claimCount(g.getId()) >= max) {
            error(player, "Tu país ya tiene el máximo de chunks reclamados (" + max + ").");
            return ActionResult.FAIL;
        }

        Block block = resolveBlock(cfg.territoryCoreBlock);

        // Provincia nueva (nombre de una sola palabra para poder usarlo en /guerra declarar).
        String name = uniqueProvinceName(tm, g.getId());
        Province prov = tm.createProvince(g.getId(), name, g.getColor());
        if (prov == null) {
            error(player, "No se pudo crear la provincia del núcleo.");
            return ActionResult.FAIL;
        }

        int claimed = claimSquare(tm, world, pos, g.getId(), prov.getId(), cfg.territoryCoreRadius, max);

        world.setBlockState(pos, block.getDefaultState());
        tm.placeCore(world, pos, g.getId(), prov.getId());

        if (cfg.territoryCoreConsumeItem && !player.isCreative()) {
            stack.decrement(1);
        }

        player.sendMessage(Text.literal("Núcleo territorial plantado. Provincia " + name + " creada ("
                + claimed + " chunk(s) reclamados). En una guerra, el enemigo debe romper este núcleo "
                + "o eliminar a tus soldados para conquistarla.").formatted(Formatting.GREEN), false);
        return ActionResult.SUCCESS;
    }

    /** Reclama los chunks que cubren el cuadrado de lado 2*radio centrado en {@code center}. */
    private static int claimSquare(TerritoryManager tm, ServerWorld world, BlockPos center,
                                   String countryId, String provinceId, int radius, int max) {
        int minCx = (center.getX() - radius) >> 4;
        int maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4;
        int maxCz = (center.getZ() + radius) >> 4;
        ChunkPos centerChunk = new ChunkPos(center);
        int claimed = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                Claim existing = tm.getClaim(world, cp);
                boolean isCenter = cp.x == centerChunk.x && cp.z == centerChunk.z;
                if (existing == null) {
                    if (max >= 0 && tm.claimCount(countryId) >= max) {
                        continue; // respeta el tope del país
                    }
                    tm.claim(world, cp, countryId, provinceId);
                    claimed++;
                } else if (isCenter && existing.countryId().equals(countryId)) {
                    // Asegura que el chunk del núcleo pertenezca a su provincia.
                    tm.claim(world, cp, countryId, provinceId);
                }
            }
        }
        return claimed;
    }

    /** Genera un nombre de provincia único de una sola palabra: Zona1, Zona2, … */
    private static String uniqueProvinceName(TerritoryManager tm, String countryId) {
        int n = tm.provincesOf(countryId).size() + 1;
        while (tm.getProvinceByName(countryId, "Zona" + n) != null) {
            n++;
        }
        return "Zona" + n;
    }

    private static Block resolveBlock(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return Blocks.BEDROCK;
        }
        Block b = Registries.BLOCK.get(identifier);
        return (b == Blocks.AIR && !"minecraft:air".equals(id)) ? Blocks.BEDROCK : b;
    }

    private static void error(ServerPlayerEntity player, String msg) {
        player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
    }
}
