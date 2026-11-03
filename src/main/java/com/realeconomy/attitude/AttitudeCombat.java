package com.realeconomy.attitude;

import com.realeconomy.compat.ModCompat;
import com.realeconomy.compat.mca.Mca;
import com.realeconomy.config.ModConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Combate según el modo de actitud de los aldeanos de MCA (parte 6). Se ejecuta por lotes y
 * solo cerca de jugadores. Fuera de una guerra: AGRESIVO ataca a monstruos hostiles (y a
 * jugadores solo si la config lo permite); NEUTRAL se defiende si le pegan; PASIVO no ataca.
 * El objetivo en guerra lo decide la parte 7.
 */
public final class AttitudeCombat {

    private static final int SCAN_INTERVAL = 5;
    private static final double DETECT_RADIUS = 16.0;
    private static int tickCounter;
    private static final Map<UUID, Long> lastAttack = new HashMap<>();

    private AttitudeCombat() {
    }

    public static void tick(MinecraftServer server) {
        if (!ModCompat.mcaEnabled()) {
            return;
        }
        if (++tickCounter % SCAN_INTERVAL != 0) {
            return;
        }
        AttitudeManager am = AttitudeManager.get(server);
        double range = ModConfig.get().villagerWorkActivationRange;
        Set<UUID> processed = new HashSet<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = p.getServerWorld();
            Box box = p.getBoundingBox().expand(range);
            List<VillagerEntity> near = world.getEntitiesByClass(VillagerEntity.class, box, Mca::isMcaVillager);
            for (VillagerEntity v : near) {
                if (!processed.add(v.getUuid())) {
                    continue;
                }
                AttitudeMode mode = am.get(v.getUuid());
                if (mode == AttitudeMode.PASIVO) {
                    continue;
                }
                try {
                    act(world, v, mode);
                } catch (Throwable ignored) {
                    // nunca romper el servidor
                }
            }
        }
    }

    private static void act(ServerWorld world, VillagerEntity v, AttitudeMode mode) {
        LivingEntity target = null;
        if (mode == AttitudeMode.AGRESIVO) {
            // En guerra: si el aldeano está alistado y en la zona, ataca al bando enemigo.
            com.realeconomy.war.WarManager wm = com.realeconomy.war.WarManager.get(world.getServer());
            com.realeconomy.war.War war = wm.activeWarForVillager(v.getUuid());
            if (war != null && wm.isInWarZone(world.getServer(), world, v.getBlockPos(), war)) {
                target = nearestWarEnemy(world, v, war);
            }
            if (target == null) {
                target = nearestMonster(world, v);
            }
            if (target == null && war == null && ModConfig.get().aggressiveAttacksPlayersOutsideWar) {
                target = nearestHittablePlayer(world, v);
            }
        } else if (mode == AttitudeMode.NEUTRAL) {
            LivingEntity attacker = v.getAttacker();
            if (attacker != null && attacker.isAlive() && v.squaredDistanceTo(attacker) <= DETECT_RADIUS * DETECT_RADIUS) {
                target = attacker;
            }
        }
        if (target != null) {
            engage(v, target);
        }
    }

    private static HostileEntity nearestMonster(ServerWorld world, VillagerEntity v) {
        Box box = v.getBoundingBox().expand(DETECT_RADIUS);
        HostileEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (HostileEntity h : world.getEntitiesByClass(HostileEntity.class, box, HostileEntity::isAlive)) {
            double d = v.squaredDistanceTo(h);
            if (d < bestDist) {
                bestDist = d;
                best = h;
            }
        }
        return best;
    }

    /** El enemigo de guerra más cercano (jugador o aldeano del bando contrario) dentro del radio y la zona. */
    private static LivingEntity nearestWarEnemy(ServerWorld world, VillagerEntity v, com.realeconomy.war.War war) {
        int side = war.villagerSide(v.getUuid());
        int enemy = side == com.realeconomy.war.War.SIDE_ATTACKER
                ? com.realeconomy.war.War.SIDE_DEFENDER : com.realeconomy.war.War.SIDE_ATTACKER;
        Set<UUID> enemyPlayers = enemy == com.realeconomy.war.War.SIDE_ATTACKER ? war.attackers() : war.defenders();
        Set<UUID> enemyVillagers = war.villagerKeys(enemy);
        Set<UUID> eliminated = war.eliminated();

        Box box = v.getBoundingBox().expand(DETECT_RADIUS);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            UUID id = e.getUuid();
            boolean isEnemy = (e instanceof PlayerEntity && enemyPlayers.contains(id) && !eliminated.contains(id))
                    || (enemyVillagers.contains(id));
            if (!isEnemy) {
                continue;
            }
            double d = v.squaredDistanceTo(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private static PlayerEntity nearestHittablePlayer(ServerWorld world, VillagerEntity v) {
        Box box = v.getBoundingBox().expand(DETECT_RADIUS);
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, box,
                pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator() && !Mca.isOwner(v, pl.getUuid()))) {
            double d = v.squaredDistanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Navega hacia el objetivo y lo golpea (con enfriamiento). Reutilizable por la parte 7. */
    public static void engage(VillagerEntity v, LivingEntity target) {
        v.getLookControl().lookAt(target.getX(), target.getEyeY(), target.getZ());
        if (v.squaredDistanceTo(target) > 4.0) {
            if (v instanceof MobEntity mob) {
                mob.getNavigation().startMovingTo(target, 0.6);
            }
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastAttack.get(v.getUuid());
        if (last == null || now - last >= 1000L) {
            v.swingHand(Hand.MAIN_HAND);
            v.tryAttack(target);
            lastAttack.put(v.getUuid(), now);
        }
    }
}
