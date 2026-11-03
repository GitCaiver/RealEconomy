package com.realeconomy.employment;

import com.realeconomy.compat.ModCompat;
import com.realeconomy.compat.mca.Mca;
import com.realeconomy.config.ModConfig;
import com.realeconomy.territory.TerritoryManager;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Comportamiento físico de los aldeanos de MCA empleados (parte 3): el minero rompe
 * bloques de verdad dentro de su zona, el leñador tala, el agricultor cosecha y replanta,
 * y el guardia protege su puesto. Se ejecuta por lotes y <b>solo cerca de jugadores / en
 * chunks cargados</b> (rendimiento). Usa API vanilla (el aldeano de MCA es un VillagerEntity),
 * sin tocar la IA interna de MCA, así que es robusto aunque cambie la versión de MCA.
 *
 * <p>Seguridad: solo rompe dentro de la zona asignada, solo en territorio propio o sin dueño
 * (nunca de otro país), nunca contenedores ni el cofre vinculado, y respeta un límite de
 * bloques/minuto. El estado de trabajo (objetivo, progreso, carga) es transitorio.
 */
public final class VillagerWorker {

    private static final int SCAN_INTERVAL = 5; // ticks entre pasadas
    private static int tickCounter;
    private static final Map<UUID, WorkState> STATES = new HashMap<>();

    private VillagerWorker() {
    }

    private static final class WorkState {
        BlockPos target;
        int progress;
        int cooldown; // guardia: enfriamiento de ataque
        boolean depositing;
        final List<ItemStack> carried = new ArrayList<>();
        int breaks;
        long windowStart = System.currentTimeMillis();

        int carriedCount() {
            int n = 0;
            for (ItemStack s : carried) {
                n += s.getCount();
            }
            return n;
        }
    }

    public static void tick(MinecraftServer server) {
        if (!ModCompat.mcaEnabled()) {
            return;
        }
        if (++tickCounter % SCAN_INTERVAL != 0) {
            return;
        }
        EmploymentManager mgr = EmploymentManager.get(server);
        if (mgr.all().isEmpty()) {
            if (!STATES.isEmpty()) {
                STATES.clear();
            }
            return;
        }
        double range = ModConfig.get().villagerWorkActivationRange;
        Set<UUID> processed = new HashSet<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = p.getServerWorld();
            Box box = p.getBoundingBox().expand(range);
            List<VillagerEntity> near = world.getEntitiesByClass(VillagerEntity.class, box,
                    e -> Mca.isMcaVillager(e) && mgr.isEmployed(e.getUuid()));
            for (VillagerEntity v : near) {
                if (!processed.add(v.getUuid())) {
                    continue;
                }
                EmployedVillager ev = mgr.get(v.getUuid());
                if (ev == null || !ev.active()) {
                    continue;
                }
                try {
                    process(server, world, v, ev);
                } catch (Throwable t) {
                    // Nunca romper el servidor por el trabajo de un aldeano.
                }
            }
        }
        STATES.keySet().removeIf(id -> !mgr.isEmployed(id));
    }

    private static void process(MinecraftServer server, ServerWorld world, VillagerEntity v, EmployedVillager ev) {
        WorkState st = STATES.computeIfAbsent(v.getUuid(), k -> new WorkState());
        switch (ev.job()) {
            case MINERO, LENADOR, AGRICULTOR -> gather(world, v, ev, st);
            case GUARDIA -> guard(world, v, ev, st);
            case LIBRERO -> {
                // El librero vende libros (parte 4): no realiza trabajo físico.
            }
        }
    }

    // ---- Recolección (minero / leñador / agricultor) ----

    private static void gather(ServerWorld world, VillagerEntity v, EmployedVillager ev, WorkState st) {
        if (!ev.hasZone() || ev.chest() == null) {
            return; // sin zona o cofre asignados no puede trabajar
        }
        // Ventana del límite por minuto.
        long now = System.currentTimeMillis();
        if (now - st.windowStart >= 60_000L) {
            st.windowStart = now;
            st.breaks = 0;
        }

        int capacity = Math.max(1, ModConfig.get().villagerCarryCapacity);
        if (st.depositing || st.carriedCount() >= capacity) {
            st.depositing = true;
            deposit(world, v, ev, st);
            return;
        }

        // Objetivo válido o buscar uno nuevo.
        if (st.target == null || !isWorkable(world, st.target, ev)) {
            st.target = findTarget(world, v, ev);
            st.progress = 0;
        }
        if (st.target == null) {
            // Sin nada que hacer cerca: acércate al centro de la zona.
            moveTo(v, Vec3d.ofCenter(ev.zoneCenter()), 0.5);
            return;
        }

        Vec3d center = Vec3d.ofCenter(st.target);
        v.getLookControl().lookAt(center.x, center.y, center.z);
        if (v.squaredDistanceTo(center) > 20.0) { // ~4.5 bloques
            moveTo(v, center, 0.5);
            return;
        }

        if (st.breaks >= Math.max(1, ModConfig.get().villagerMaxBreaksPerMinute)) {
            return; // límite alcanzado este minuto
        }

        // Picar: progreso, animación y grietas.
        BlockState state = world.getBlockState(st.target);
        int required = requiredScans(state, world, st.target);
        st.progress++;
        v.swingHand(Hand.MAIN_HAND);
        int stage = Math.min(9, (int) ((st.progress / (float) required) * 10));
        world.setBlockBreakingInfo(v.getId(), st.target, stage);

        if (st.progress >= required) {
            doHarvest(world, v, ev, st, st.target);
            world.setBlockBreakingInfo(v.getId(), st.target, -1);
            st.breaks++;
            st.progress = 0;
            st.target = null;
        }
    }

    private static int requiredScans(BlockState state, ServerWorld world, BlockPos pos) {
        float hardness;
        try {
            hardness = state.getHardness(world, pos);
        } catch (Throwable t) {
            hardness = 1.0f;
        }
        if (hardness < 0) {
            hardness = 1.0f; // inquebrantable teórico: trátalo como blando
        }
        return Math.max(2, Math.min(40, (int) (hardness * 4) + 2));
    }

    private static void doHarvest(ServerWorld world, VillagerEntity v, EmployedVillager ev, WorkState st, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // Agricultor: cosecha el cultivo maduro y lo replanta a edad 0.
        if (block instanceof CropBlock crop && crop.isMature(state)) {
            addDrops(world, pos, state, Items.NETHERITE_HOE, st);
            world.setBlockState(pos, crop.withAge(0), Block.NOTIFY_LISTENERS);
            return;
        }

        // Minero / leñador: romper de verdad y quedarse los drops (con la herramienta adecuada).
        ItemStack tool = ev.job() == VillagerJob.LENADOR
                ? new ItemStack(Items.NETHERITE_AXE) : new ItemStack(Items.NETHERITE_PICKAXE);
        boolean wasLog = state.isIn(BlockTags.LOGS);
        BlockState below = world.getBlockState(pos.down());
        addDrops(world, pos, state, tool.getItem(), st);
        world.breakBlock(pos, false, v);

        // Leñador: replanta un brote si taló la base de un árbol sobre tierra/hierba.
        if (ev.job() == VillagerJob.LENADOR && ModConfig.get().loggerReplantSapling && wasLog
                && (below.isOf(Blocks.GRASS_BLOCK) || below.isOf(Blocks.DIRT) || below.isOf(Blocks.PODZOL)
                    || below.isOf(Blocks.ROOTED_DIRT) || below.isOf(Blocks.COARSE_DIRT))) {
            Block sapling = saplingFor(block);
            if (sapling != null && world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, sapling.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static void addDrops(ServerWorld world, BlockPos pos, BlockState state, net.minecraft.item.Item tool, WorkState st) {
        BlockEntity be = world.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, be, null, new ItemStack(tool));
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                st.carried.add(drop);
            }
        }
    }

    private static Block saplingFor(Block log) {
        Identifier id = Registries.BLOCK.getId(log);
        String path = id.getPath().replace("_log", "").replace("stripped_", "").replace("_wood", "");
        Block sapling = Registries.BLOCK.get(Identifier.of("minecraft", path + "_sapling"));
        if (sapling != Blocks.AIR) {
            return sapling;
        }
        // Casos especiales.
        return switch (path) {
            case "crimson", "warped" -> null; // hongos del Nether: sin brote estándar
            default -> null;
        };
    }

    // ---- Depósito en el cofre ----

    private static void deposit(ServerWorld world, VillagerEntity v, EmployedVillager ev, WorkState st) {
        BlockPos chest = ev.chest();
        Vec3d c = Vec3d.ofCenter(chest);
        if (v.squaredDistanceTo(c) > 9.0) { // ~3 bloques
            moveTo(v, c, 0.5);
            return;
        }
        BlockEntity be = world.getBlockEntity(chest);
        if (!(be instanceof Inventory inv)) {
            // El cofre ya no existe: deja de intentar depositar.
            st.carried.clear();
            st.depositing = false;
            return;
        }
        st.carried.removeIf(stack -> insert(inv, stack).isEmpty());
        // Si lo que queda no cabe, se queda cargado hasta que haya hueco.
        if (st.carried.isEmpty()) {
            st.depositing = false;
        }
        inv.markDirty();
    }

    /** Inserta lo que pueda en el inventario y devuelve el sobrante. */
    private static ItemStack insert(Inventory inv, ItemStack stack) {
        for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) {
                inv.setStack(i, stack.copy());
                stack.setCount(0);
                return ItemStack.EMPTY;
            }
            if (ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                int max = Math.min(slot.getMaxCount(), inv.getMaxCountPerStack());
                int move = Math.min(stack.getCount(), max - slot.getCount());
                if (move > 0) {
                    slot.increment(move);
                    stack.decrement(move);
                }
            }
        }
        return stack;
    }

    // ---- Objetivo de trabajo ----

    private static BlockPos findTarget(ServerWorld world, VillagerEntity v, EmployedVillager ev) {
        int r = Math.max(2, ModConfig.get().villagerSearchRadius);
        BlockPos origin = v.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!isWorkable(world, m, ev)) {
                        continue;
                    }
                    double d = origin.getSquaredDistance(m);
                    if (d < bestDist) {
                        bestDist = d;
                        best = m.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    /** ¿Puede este trabajo actuar sobre este bloque aquí (zona, territorio y tipo)? */
    private static boolean isWorkable(ServerWorld world, BlockPos pos, EmployedVillager ev) {
        String dim = world.getRegistryKey().getValue().toString();
        if (!ev.inZone(dim, pos)) {
            return false;
        }
        if (pos.equals(ev.chest())) {
            return false;
        }
        if (!TerritoryManager.get(world.getServer()).canWorkAt(world, pos, ev.countryId())) {
            return false; // nunca en territorio de otro país
        }
        if (com.realeconomy.war.WarProtection.isProtected(world.getServer(), world, pos)) {
            return false; // terreno protegido en una zona de guerra (si warBreakStructures=false)
        }
        if (world.getBlockEntity(pos) != null) {
            return false; // nunca contenedores / bloques con entidad
        }
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        return switch (ev.job()) {
            case MINERO -> isMineable(state);
            case LENADOR -> state.isIn(BlockTags.LOGS)
                    || (ModConfig.get().loggerBreakLeaves && state.isIn(BlockTags.LEAVES));
            case AGRICULTOR -> state.getBlock() instanceof CropBlock crop && crop.isMature(state);
            default -> false;
        };
    }

    private static boolean isMineable(BlockState state) {
        if (state.isIn(ConventionalBlockTags.ORES)) {
            return true;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return ModConfig.get().minerExtraBlocks.contains(id.toString());
    }

    // ---- Guardia ----

    private static void guard(ServerWorld world, VillagerEntity v, EmployedVillager ev, WorkState st) {
        if (st.cooldown > 0) {
            st.cooldown--;
        }
        BlockPos post = ev.hasZone() ? ev.zoneCenter() : v.getBlockPos();
        int radius = ev.hasZone() ? ev.zoneRadius() : 8;

        HostileEntity monster = null;
        if (ModConfig.get().guardAttackMonsters) {
            Box box = new Box(post).expand(radius);
            double best = Double.MAX_VALUE;
            for (HostileEntity h : world.getEntitiesByClass(HostileEntity.class, box, HostileEntity::isAlive)) {
                double d = v.squaredDistanceTo(h);
                if (d < best) {
                    best = d;
                    monster = h;
                }
            }
        }

        if (monster != null) {
            v.getLookControl().lookAt(monster.getX(), monster.getEyeY(), monster.getZ());
            if (v.squaredDistanceTo(monster) > 4.0) {
                moveTo(v, monster.getPos(), 0.6);
            } else if (st.cooldown <= 0) {
                v.swingHand(Hand.MAIN_HAND);
                v.tryAttack(monster);
                st.cooldown = 4; // ~1s entre golpes (4 pasadas de 5 ticks)
            }
            return;
        }

        // Sin enemigos: vuelve a su puesto.
        Vec3d c = Vec3d.ofCenter(post);
        if (v.squaredDistanceTo(c) > 9.0) {
            moveTo(v, c, 0.5);
        }
    }

    // ---- Utilidad de movimiento ----

    private static void moveTo(VillagerEntity v, Vec3d pos, double speed) {
        if (v instanceof MobEntity mob) {
            mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, speed);
        }
    }
}
