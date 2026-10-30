package com.realeconomy.jobs;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Engancha los eventos que disparan el cobro por trabajo (parte 8):
 * minería (minerales), tala (troncos) y agricultura (cultivos maduros).
 * La banca se cobra desde {@link com.realeconomy.bank.BankActions}.
 */
public final class JobEvents {

    private JobEvents() {
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            Job job = classify(state);
            if (job != null) {
                Jobs.payWage(serverPlayer, job);
            }
        });
    }

    /** Devuelve el trabajo asociado a romper este bloque, o null si no cuenta. */
    private static Job classify(BlockState state) {
        if (state.isIn(ConventionalBlockTags.ORES)) {
            return Job.MINING;
        }
        if (state.isIn(BlockTags.LOGS)) {
            return Job.LOGGING;
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
            return Job.FARMING;
        }
        return null;
    }
}
