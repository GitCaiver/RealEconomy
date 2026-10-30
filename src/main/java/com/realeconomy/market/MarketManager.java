package com.realeconomy.market;

import com.realeconomy.RealEconomy;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_markets";

    public static final PersistentState.Type<MarketManager> TYPE = new PersistentState.Type<>(
            MarketManager::new,
            MarketManager::fromNbt,
            null
    );

    private final Map<String, Market> markets = new HashMap<>();

    public static MarketManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    public Market getById(String id) {
        return markets.get(id);
    }

    public int countByGroup(String groupId) {
        int n = 0;
        for (Market m : markets.values()) {
            if (m.getGroupId().equals(groupId)) {
                n++;
            }
        }
        return n;
    }

    public Market createMarket(String groupId, String groupName, String groupTag) {
        Market market = Market.create(groupId, groupName, groupTag);
        market.getStorage().setOnChanged(this::markDirty);
        markets.put(market.getId(), market);
        markDirty();
        return market;
    }

    /** Elimina el mercado y devuelve el contenido de la caja. */
    public List<ItemStack> removeMarket(String id) {
        Market market = markets.remove(id);
        List<ItemStack> contents = new ArrayList<>();
        if (market != null) {
            for (int i = 0; i < market.getStorage().size(); i++) {
                ItemStack stack = market.getStorage().getStack(i);
                if (!stack.isEmpty()) {
                    contents.add(stack.copy());
                }
            }
            markDirty();
        }
        return contents;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Market m : markets.values()) {
            list.add(m.toNbt(registryLookup));
        }
        nbt.put("markets", list);
        return nbt;
    }

    private static MarketManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MarketManager mgr = new MarketManager();
        NbtList list = nbt.getList("markets", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Market market = Market.fromNbt(list.getCompound(i), registryLookup);
            market.getStorage().setOnChanged(mgr::markDirty);
            mgr.markets.put(market.getId(), market);
        }
        return mgr;
    }
}
