package com.realeconomy.auction;

import com.realeconomy.RealEconomy;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado persistente de las subastas: pedestales (posiciones), subastas activas
 * y entregas pendientes para jugadores desconectados. Guardado en el overworld.
 */
public class AuctionManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_auctions";

    public static final PersistentState.Type<AuctionManager> TYPE = new PersistentState.Type<>(
            AuctionManager::new,
            AuctionManager::fromNbt,
            null
    );

    /** Posiciones (BlockPos.asLong) de los pedestales. */
    private final Set<Long> pedestals = new HashSet<>();
    /** id -> subasta activa. */
    private final Map<Integer, Auction> auctions = new HashMap<>();
    /** Entregas pendientes de items (jugador desconectado al cerrarse/devolverse). */
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();
    private int nextId = 1;

    public static AuctionManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    // ---- Pedestales ----

    public boolean addPedestal(BlockPos pos) {
        boolean added = pedestals.add(pos.asLong());
        if (added) {
            markDirty();
        }
        return added;
    }

    public boolean isPedestal(BlockPos pos) {
        return pedestals.contains(pos.asLong());
    }

    public void removePedestal(BlockPos pos) {
        if (pedestals.remove(pos.asLong())) {
            markDirty();
        }
    }

    public Set<Long> pedestals() {
        return pedestals;
    }

    // ---- Subastas ----

    public Auction auctionAt(long posLong) {
        for (Auction a : auctions.values()) {
            if (a.getPedestal() == posLong) {
                return a;
            }
        }
        return null;
    }

    public Auction byId(int id) {
        return auctions.get(id);
    }

    public java.util.Collection<Auction> all() {
        return auctions.values();
    }

    public Auction create(long pedestal, UUID seller, String sellerName, ItemStack item,
                          long startPrice, String currencyId, long endTime) {
        Auction a = new Auction(nextId++, pedestal, seller, sellerName, item, startPrice, currencyId, endTime);
        auctions.put(a.getId(), a);
        markDirty();
        return a;
    }

    public void remove(Auction a) {
        auctions.remove(a.getId());
        markDirty();
    }

    /** Pedestal libre más cercano a una posición, dentro del alcance; -1 si ninguno. */
    public long nearestFreePedestal(Vec3d pos, double range) {
        return nearest(pos, range, true);
    }

    /** Pedestal (ocupado o no) más cercano a una posición; -1 si ninguno. */
    public long nearestPedestal(Vec3d pos, double range) {
        return nearest(pos, range, false);
    }

    private long nearest(Vec3d pos, double range, boolean onlyFree) {
        double best = range * range;
        long bestPos = -1L;
        for (long p : pedestals) {
            if (onlyFree && auctionAt(p) != null) {
                continue;
            }
            BlockPos bp = BlockPos.fromLong(p);
            double d = pos.squaredDistanceTo(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
            if (d <= best) {
                best = d;
                bestPos = p;
            }
        }
        return bestPos;
    }

    // ---- Entregas pendientes (offline) ----

    public void addPending(UUID player, ItemStack stack) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
        markDirty();
    }

    public List<ItemStack> takePending(UUID player) {
        List<ItemStack> list = pending.remove(player);
        if (list != null) {
            markDirty();
        }
        return list == null ? List.of() : list;
    }

    public boolean hasPending(UUID player) {
        List<ItemStack> l = pending.get(player);
        return l != null && !l.isEmpty();
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList peds = new NbtList();
        for (long p : pedestals) {
            NbtCompound c = new NbtCompound();
            c.putLong("pos", p);
            peds.add(c);
        }
        nbt.put("pedestals", peds);

        NbtList aucs = new NbtList();
        for (Auction a : auctions.values()) {
            aucs.add(a.toNbt(lookup));
        }
        nbt.put("auctions", aucs);
        nbt.putInt("nextId", nextId);

        NbtList pend = new NbtList();
        for (Map.Entry<UUID, List<ItemStack>> e : pending.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            NbtCompound c = new NbtCompound();
            c.putUuid("uuid", e.getKey());
            NbtList items = new NbtList();
            for (ItemStack s : e.getValue()) {
                items.add(s.encode(lookup));
            }
            c.put("items", items);
            pend.add(c);
        }
        nbt.put("pending", pend);
        return nbt;
    }

    private static AuctionManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        AuctionManager mgr = new AuctionManager();
        NbtList peds = nbt.getList("pedestals", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < peds.size(); i++) {
            mgr.pedestals.add(peds.getCompound(i).getLong("pos"));
        }
        NbtList aucs = nbt.getList("auctions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < aucs.size(); i++) {
            Auction a = Auction.fromNbt(aucs.getCompound(i), lookup);
            mgr.auctions.put(a.getId(), a);
        }
        mgr.nextId = Math.max(1, nbt.getInt("nextId"));
        NbtList pend = nbt.getList("pending", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < pend.size(); i++) {
            NbtCompound c = pend.getCompound(i);
            List<ItemStack> list = new ArrayList<>();
            NbtList items = c.getList("items", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < items.size(); j++) {
                ItemStack.fromNbt(lookup, items.getCompound(j)).ifPresent(list::add);
            }
            if (!list.isEmpty()) {
                mgr.pending.put(c.getUuid("uuid"), list);
            }
        }
        return mgr;
    }
}
