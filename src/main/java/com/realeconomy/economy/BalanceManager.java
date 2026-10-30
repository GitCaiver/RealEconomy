package com.realeconomy.economy;

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
 * Libro de saldos: dinero "en cuenta" (no físico) por jugador y moneda.
 * Lo usan el banco, las tiendas, los impuestos, etc. Las monedas físicas son
 * items ({@link com.realeconomy.item.ModItems#COIN}); esto es el saldo digital.
 */
public class BalanceManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_balances";

    public static final PersistentState.Type<BalanceManager> TYPE = new PersistentState.Type<>(
            BalanceManager::new,
            BalanceManager::fromNbt,
            null
    );

    /** playerUuid -> (currencyId -> cantidad). */
    private final Map<UUID, Map<String, Long>> balances = new HashMap<>();

    public static BalanceManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    public long get(UUID player, String currencyId) {
        Map<String, Long> m = balances.get(player);
        if (m == null) {
            return 0L;
        }
        return m.getOrDefault(currencyId, 0L);
    }

    public Map<String, Long> getAll(UUID player) {
        return balances.getOrDefault(player, Map.of());
    }

    public void add(UUID player, String currencyId, long amount) {
        if (amount == 0) {
            return;
        }
        Map<String, Long> m = balances.computeIfAbsent(player, k -> new HashMap<>());
        long next = m.getOrDefault(currencyId, 0L) + amount;
        if (next <= 0) {
            m.remove(currencyId);
        } else {
            m.put(currencyId, next);
        }
        markDirty();
    }

    /** Resta {@code amount} si hay saldo suficiente. Devuelve false si no lo hay. */
    public boolean subtract(UUID player, String currencyId, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (get(player, currencyId) < amount) {
            return false;
        }
        add(player, currencyId, -amount);
        return true;
    }

    /** Elimina todo el saldo de un titular (jugador o cuenta de grupo). */
    public void clear(UUID holder) {
        if (balances.remove(holder) != null) {
            markDirty();
        }
    }

    /** Transfiere de un jugador a otro si hay saldo. Devuelve false si no. */
    public boolean transfer(UUID from, UUID to, String currencyId, long amount) {
        if (amount <= 0) {
            return false;
        }
        if (!subtract(from, currencyId, amount)) {
            return false;
        }
        add(to, currencyId, amount);
        return true;
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList players = new NbtList();
        for (Map.Entry<UUID, Map<String, Long>> e : balances.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            NbtCompound pc = new NbtCompound();
            pc.putUuid("uuid", e.getKey());
            NbtCompound amounts = new NbtCompound();
            for (Map.Entry<String, Long> b : e.getValue().entrySet()) {
                amounts.putLong(b.getKey(), b.getValue());
            }
            pc.put("amounts", amounts);
            players.add(pc);
        }
        nbt.put("players", players);
        return nbt;
    }

    private static BalanceManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        BalanceManager mgr = new BalanceManager();
        NbtList players = nbt.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound pc = players.getCompound(i);
            UUID uuid = pc.getUuid("uuid");
            NbtCompound amounts = pc.getCompound("amounts");
            Map<String, Long> m = new HashMap<>();
            for (String key : amounts.getKeys()) {
                m.put(key, amounts.getLong(key));
            }
            if (!m.isEmpty()) {
                mgr.balances.put(uuid, m);
            }
        }
        return mgr;
    }
}
