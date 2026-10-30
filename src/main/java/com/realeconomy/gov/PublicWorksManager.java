package com.realeconomy.gov;

import com.realeconomy.RealEconomy;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Registro persistente de obras públicas (parte 3, 2ª tanda). Los ids son
 * globales (únicos en el servidor) para que cualquier jugador pueda entregar en
 * una obra por su id, sea del país que sea.
 */
public class PublicWorksManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_publicworks";

    public static final PersistentState.Type<PublicWorksManager> TYPE = new PersistentState.Type<>(
            PublicWorksManager::new,
            PublicWorksManager::fromNbt,
            null
    );

    private final List<PublicWork> works = new ArrayList<>();
    private int nextId = 1;

    public static PublicWorksManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    /** Todas las obras abiertas del servidor. */
    public List<PublicWork> all() {
        return works;
    }

    /** Obras de un país concreto. */
    public List<PublicWork> list(String countryId) {
        List<PublicWork> out = new ArrayList<>();
        for (PublicWork w : works) {
            if (w.getCountryId().equals(countryId)) {
                out.add(w);
            }
        }
        return out;
    }

    public PublicWork get(int id) {
        for (PublicWork w : works) {
            if (w.getId() == id) {
                return w;
            }
        }
        return null;
    }

    public PublicWork create(String countryId, String itemId, int required, long pricePerUnit, String currencyId) {
        PublicWork w = new PublicWork(nextId++, countryId, itemId, required, 0, pricePerUnit, currencyId);
        works.add(w);
        markDirty();
        return w;
    }

    public boolean remove(int id) {
        boolean removed = works.removeIf(w -> w.getId() == id);
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public void removeCountry(String countryId) {
        if (works.removeIf(w -> w.getCountryId().equals(countryId))) {
            markDirty();
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (PublicWork w : works) {
            list.add(w.toNbt());
        }
        nbt.put("works", list);
        nbt.putInt("nextId", nextId);
        return nbt;
    }

    private static PublicWorksManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        PublicWorksManager mgr = new PublicWorksManager();
        NbtList list = nbt.getList("works", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            mgr.works.add(PublicWork.fromNbt(list.getCompound(i)));
        }
        mgr.nextId = Math.max(1, nbt.getInt("nextId"));
        return mgr;
    }
}
