package com.realeconomy.buyer;

import com.realeconomy.config.ModConfig;
import com.realeconomy.group.Group;
import com.realeconomy.npc.NpcUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

/** NPC comprador de un país: aldeano marcado con la tag de comprador y el id del país. */
public final class BuyerEntities {

    public static final String TAG = "realeconomy_buyer";
    private static final String COUNTRY_PREFIX = "rebuyercountry_";

    private BuyerEntities() {
    }

    public static VillagerEntity spawn(ServerPlayerEntity player, Group country) {
        VillagerEntity npc = NpcUtil.spawn(player, TAG, "🛒 Comprador [" + country.getTag() + "]");
        npc.addCommandTag(COUNTRY_PREFIX + country.getId());
        return npc;
    }

    public static boolean isBuyer(VillagerEntity entity) {
        return NpcUtil.hasTag(entity, TAG);
    }

    public static String countryIdOf(VillagerEntity entity) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(COUNTRY_PREFIX)) {
                return tag.substring(COUNTRY_PREFIX.length());
            }
        }
        return null;
    }

    /** Comprador más cercano al jugador dentro del alcance, o null. */
    public static VillagerEntity nearest(ServerPlayerEntity player) {
        double range = ModConfig.get().buyerRange;
        Box box = player.getBoundingBox().expand(range);
        List<VillagerEntity> list = player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, BuyerEntities::isBuyer);
        list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
        return list.isEmpty() ? null : list.get(0);
    }

    public static boolean removeNearest(ServerPlayerEntity player) {
        double range = Math.max(8.0, ModConfig.get().buyerRange);
        Box box = player.getBoundingBox().expand(range);
        List<VillagerEntity> list = player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, BuyerEntities::isBuyer);
        list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
        if (list.isEmpty()) {
            return false;
        }
        list.get(0).discard();
        return true;
    }
}
