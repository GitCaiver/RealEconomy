package com.realeconomy.npc;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/** Utilidades comunes para los NPC del mod (aldeanos marcados y sin IA). */
public final class NpcUtil {

    private NpcUtil() {
    }

    public static VillagerEntity spawn(ServerPlayerEntity player, String tag, String name) {
        ServerWorld world = player.getServerWorld();
        VillagerEntity npc = new VillagerEntity(EntityType.VILLAGER, world);
        npc.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw() + 180.0F, 0.0F);
        npc.setAiDisabled(true);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setPersistent();
        npc.setCustomName(Text.literal(name));
        npc.setCustomNameVisible(true);
        npc.setBaby(false);
        npc.addCommandTag(tag);
        world.spawnEntity(npc);
        return npc;
    }

    public static boolean hasTag(VillagerEntity entity, String tag) {
        return entity.getCommandTags().contains(tag);
    }

    public static boolean removeNearest(ServerWorld world, Vec3d pos, double range, String tag) {
        Box box = Box.of(pos, range * 2, range * 2, range * 2);
        List<VillagerEntity> list = world.getEntitiesByClass(VillagerEntity.class, box, e -> hasTag(e, tag));
        list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(pos)));
        if (list.isEmpty()) {
            return false;
        }
        list.get(0).discard();
        return true;
    }

    public static boolean isNear(ServerPlayerEntity player, String tag, double range) {
        Box box = player.getBoundingBox().expand(range);
        return !player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, e -> hasTag(e, tag))
                .isEmpty();
    }
}
