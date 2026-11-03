package com.realeconomy.attitude;

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
 * Modo de actitud por aldeano de MCA (MCA no tiene este concepto, así que lo guarda
 * RealEconomy). Por defecto PASIVO. Persistente en el mundo.
 */
public class AttitudeManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_attitude";

    public static final PersistentState.Type<AttitudeManager> TYPE = new PersistentState.Type<>(
            AttitudeManager::new,
            AttitudeManager::fromNbt,
            null
    );

    private final Map<UUID, AttitudeMode> modes = new HashMap<>();

    public AttitudeManager() {
    }

    public static AttitudeManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    public AttitudeMode get(UUID villager) {
        return modes.getOrDefault(villager, AttitudeMode.PASIVO);
    }

    public boolean has(UUID villager) {
        return modes.containsKey(villager);
    }

    public void set(UUID villager, AttitudeMode mode) {
        if (mode == null || mode == AttitudeMode.PASIVO) {
            modes.remove(villager); // PASIVO es el defecto: no hace falta guardarlo
        } else {
            modes.put(villager, mode);
        }
        markDirty();
    }

    public void remove(UUID villager) {
        if (modes.remove(villager) != null) {
            markDirty();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, AttitudeMode> e : modes.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("v", e.getKey());
            c.putString("m", e.getValue().key());
            list.add(c);
        }
        nbt.put("modes", list);
        return nbt;
    }

    private static AttitudeManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        AttitudeManager mgr = new AttitudeManager();
        NbtList list = nbt.getList("modes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            AttitudeMode m = AttitudeMode.parse(c.getString("m"));
            if (m != null) {
                mgr.modes.put(c.getUuid("v"), m);
            }
        }
        return mgr;
    }
}
