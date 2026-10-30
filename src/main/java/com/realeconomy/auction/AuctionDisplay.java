package com.realeconomy.auction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Representación visual de una subasta: un item flotante (sin gravedad, sin
 * recogida) sobre el pedestal, que rota solo y muestra el estado en su nombre.
 */
public final class AuctionDisplay {

    private AuctionDisplay() {
    }

    public static UUID spawn(ServerWorld world, BlockPos pedestal, ItemStack item, Text name) {
        ItemEntity e = new ItemEntity(world,
                pedestal.getX() + 0.5, pedestal.getY() + 1.2, pedestal.getZ() + 0.5, item.copy());
        e.setNoGravity(true);
        e.setNeverDespawn();
        e.setPickupDelayInfinite();
        e.setVelocity(Vec3d.ZERO);
        e.setCustomName(name);
        e.setCustomNameVisible(true);
        world.spawnEntity(e);
        return e.getUuid();
    }

    public static void update(ServerWorld world, UUID uuid, Text name) {
        if (uuid == null) {
            return;
        }
        Entity e = world.getEntity(uuid);
        if (e instanceof ItemEntity ie) {
            ie.setVelocity(Vec3d.ZERO);
            ie.setNoGravity(true);
            ie.setNeverDespawn();
            ie.setPickupDelayInfinite();
            ie.setCustomName(name);
            ie.setCustomNameVisible(true);
        }
    }

    public static void remove(ServerWorld world, UUID uuid) {
        if (uuid == null) {
            return;
        }
        Entity e = world.getEntity(uuid);
        if (e != null) {
            e.discard();
        }
    }
}
