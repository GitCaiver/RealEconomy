package com.realeconomy.shop;

import com.realeconomy.config.ModConfig;
import com.realeconomy.npc.NpcUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

/** NPC de tienda: aldeano con la tag de tienda y una tag con el id de la tienda. */
public final class ShopEntities {

    public static final String TAG = "realeconomy_shop";
    private static final String ID_PREFIX = "reshopid_";

    private ShopEntities() {
    }

    public static VillagerEntity spawn(ServerPlayerEntity player, Shop shop) {
        VillagerEntity npc = NpcUtil.spawn(player, TAG, "🏪 Tienda de " + shop.getOwnerName());
        npc.addCommandTag(ID_PREFIX + shop.getId());
        return npc;
    }

    public static boolean isShop(VillagerEntity entity) {
        return NpcUtil.hasTag(entity, TAG);
    }

    public static String shopIdOf(VillagerEntity entity) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(ID_PREFIX)) {
                return tag.substring(ID_PREFIX.length());
            }
        }
        return null;
    }

    /** Id de la tienda propia más cercana al jugador (dentro del alcance), o null. */
    public static String nearestOwned(ServerPlayerEntity player, ShopManager manager) {
        double range = ModConfig.get().shopRange;
        Box box = player.getBoundingBox().expand(range);
        List<VillagerEntity> list = player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, ShopEntities::isShop);
        list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));
        for (VillagerEntity npc : list) {
            String id = shopIdOf(npc);
            Shop shop = id == null ? null : manager.getById(id);
            if (shop != null && shop.getOwner().equals(player.getUuid())) {
                return id;
            }
        }
        return null;
    }

    public static boolean isNearShop(ServerPlayerEntity player, String shopId) {
        double range = ModConfig.get().shopRange;
        Box box = player.getBoundingBox().expand(range);
        List<VillagerEntity> list = player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, ShopEntities::isShop);
        for (VillagerEntity npc : list) {
            if (shopId.equals(shopIdOf(npc))) {
                return true;
            }
        }
        return false;
    }

    /** Elimina el NPC de una tienda concreta cercano al jugador. */
    public static boolean removeNpc(ServerPlayerEntity player, String shopId) {
        double range = Math.max(8.0, ModConfig.get().shopRange);
        Box box = player.getBoundingBox().expand(range);
        List<VillagerEntity> list = player.getServerWorld()
                .getEntitiesByClass(VillagerEntity.class, box, ShopEntities::isShop);
        for (VillagerEntity npc : list) {
            if (shopId.equals(shopIdOf(npc))) {
                npc.discard();
                return true;
            }
        }
        return false;
    }
}
