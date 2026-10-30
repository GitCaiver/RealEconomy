package com.realeconomy.townhall;

import com.realeconomy.RealEconomy;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Licencias de tienda disponibles (sin usar) por jugador. Comprar en el
 * ayuntamiento suma 1; crear una tienda (parte 5) consume 1. Una licencia
 * usada no se puede reutilizar.
 */
public class LicenseManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_licenses";

    public static final PersistentState.Type<LicenseManager> TYPE = new PersistentState.Type<>(
            LicenseManager::new,
            LicenseManager::fromNbt,
            null
    );

    /** playerUuid -> licencias de tienda disponibles. */
    private final Map<UUID, Integer> available = new HashMap<>();
    /** playerUuid -> licencias de subasta disponibles (parte 5, 2ª tanda). */
    private final Map<UUID, Integer> auctionAvailable = new HashMap<>();

    public static LicenseManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    public int get(UUID player) {
        return available.getOrDefault(player, 0);
    }

    public void add(UUID player, int amount) {
        if (amount == 0) {
            return;
        }
        int next = get(player) + amount;
        if (next <= 0) {
            available.remove(player);
        } else {
            available.put(player, next);
        }
        markDirty();
    }

    /** Consume una licencia si hay alguna disponible. */
    public boolean consume(UUID player) {
        if (get(player) <= 0) {
            return false;
        }
        add(player, -1);
        return true;
    }

    // ---- Licencias de subasta (parte 5) ----

    public int getAuction(UUID player) {
        return auctionAvailable.getOrDefault(player, 0);
    }

    public void addAuction(UUID player, int amount) {
        if (amount == 0) {
            return;
        }
        int next = getAuction(player) + amount;
        if (next <= 0) {
            auctionAvailable.remove(player);
        } else {
            auctionAvailable.put(player, next);
        }
        markDirty();
    }

    public boolean consumeAuction(UUID player) {
        if (getAuction(player) <= 0) {
            return false;
        }
        addAuction(player, -1);
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, Integer> e : available.entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            NbtCompound c = new NbtCompound();
            c.putUuid("uuid", e.getKey());
            c.putInt("count", e.getValue());
            list.add(c);
        }
        nbt.put("licenses", list);
        NbtList auctions = new NbtList();
        for (Map.Entry<UUID, Integer> e : auctionAvailable.entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            NbtCompound c = new NbtCompound();
            c.putUuid("uuid", e.getKey());
            c.putInt("count", e.getValue());
            auctions.add(c);
        }
        nbt.put("auctionLicenses", auctions);
        return nbt;
    }

    private static LicenseManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        LicenseManager mgr = new LicenseManager();
        NbtList list = nbt.getList("licenses", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            mgr.available.put(c.getUuid("uuid"), c.getInt("count"));
        }
        NbtList auctions = nbt.getList("auctionLicenses", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < auctions.size(); i++) {
            NbtCompound c = auctions.getCompound(i);
            mgr.auctionAvailable.put(c.getUuid("uuid"), c.getInt("count"));
        }
        return mgr;
    }
}
