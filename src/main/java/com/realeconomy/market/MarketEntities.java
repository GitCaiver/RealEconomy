package com.realeconomy.market;

import com.realeconomy.config.ModConfig;
import com.realeconomy.npc.NpcUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

/** NPC de mercado de grupo: aldeano con la tag de mercado y una tag con el id. */
public final class MarketEntities {

    public static final String TAG = "realeconomy_market";
    private static final String ID_PREFIX = "remarketid_";

    private MarketEntities() {
    }

    public static VillagerEntity spawn(ServerPlayerEntity player, Market market) {
        VillagerEntity npc = NpcUtil.spawn(player, TAG, "🏪 Mercado [" + market.getGroupTag() + "]");
        npc.addCommandTag(ID_PREFIX + market.getId());
        return npc;
    }

    public static boolean isMarket(VillagerEntity entity) {
        return NpcUtil.hasTag(entity, TAG);
    }

    public static String marketIdOf(VillagerEntity entity) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(ID_PREFIX)) {
                return tag.substring(ID_PREFIX.length());
            }
        }
        return null;
    }

    /** Id del mercado del grupo del jugador más cercano (dentro del alcance), o null. */
    public static String nearestOfGroup(ServerPlayerEntity player, MarketManager manager, String groupId) {
        double range = ModConfig.get().marketRange;
        Box box = player.getBoundingBox().expand(range);
        List<VillagerEntity> list = player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, MarketEntities::isMarket);
        list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
        for (VillagerEntity npc : list) {
            String id = marketIdOf(npc);
            Market market = id == null ? null : manager.getById(id);
            if (market != null && market.getGroupId().equals(groupId)) {
                return id;
            }
        }
        return null;
    }

    public static boolean isNearMarket(ServerPlayerEntity player, String marketId) {
        double range = ModConfig.get().marketRange;
        Box box = player.getBoundingBox().expand(range);
        List<VillagerEntity> list = player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, MarketEntities::isMarket);
        for (VillagerEntity npc : list) {
            if (marketId.equals(marketIdOf(npc))) {
                return true;
            }
        }
        return false;
    }

    public static boolean removeNpc(ServerPlayerEntity player, String marketId) {
        double range = Math.max(8.0, ModConfig.get().marketRange);
        Box box = player.getBoundingBox().expand(range);
        List<VillagerEntity> list = player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, MarketEntities::isMarket);
        for (VillagerEntity npc : list) {
            if (marketId.equals(marketIdOf(npc))) {
                npc.discard();
                return true;
            }
        }
        return false;
    }
}
