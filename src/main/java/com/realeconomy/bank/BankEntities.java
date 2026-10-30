package com.realeconomy.bank;

import com.realeconomy.config.ModConfig;
import com.realeconomy.npc.NpcUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Utilidades para el NPC del banco (un aldeano marcado y sin IA). */
public final class BankEntities {

    public static final String TAG = "realeconomy_bank";

    private BankEntities() {
    }

    public static VillagerEntity spawn(ServerPlayerEntity player) {
        return NpcUtil.spawn(player, TAG, "🏦 Banco");
    }

    public static boolean isBank(VillagerEntity entity) {
        return NpcUtil.hasTag(entity, TAG);
    }

    public static boolean removeNearest(ServerWorld world, Vec3d pos, double range) {
        return NpcUtil.removeNearest(world, pos, range, TAG);
    }

    public static boolean isNearBank(ServerPlayerEntity player) {
        return NpcUtil.isNear(player, TAG, ModConfig.get().bankRange);
    }
}
