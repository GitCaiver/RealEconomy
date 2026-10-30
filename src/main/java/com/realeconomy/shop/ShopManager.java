package com.realeconomy.shop;

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
import java.util.UUID;

public class ShopManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_shops";

    public static final PersistentState.Type<ShopManager> TYPE = new PersistentState.Type<>(
            ShopManager::new,
            ShopManager::fromNbt,
            null
    );

    private final Map<String, Shop> shops = new HashMap<>();

    public static ShopManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    public Shop getById(String id) {
        return shops.get(id);
    }

    public int countByOwner(UUID owner) {
        int n = 0;
        for (Shop s : shops.values()) {
            if (s.getOwner().equals(owner)) {
                n++;
            }
        }
        return n;
    }

    public Shop createShop(UUID owner, String ownerName) {
        Shop shop = Shop.create(owner, ownerName);
        shop.getStorage().setOnChanged(this::markDirty);
        shops.put(shop.getId(), shop);
        markDirty();
        return shop;
    }

    /** Elimina la tienda y devuelve el contenido del almacén (para soltarlo/devolverlo). */
    public List<ItemStack> removeShop(String id) {
        Shop shop = shops.remove(id);
        List<ItemStack> contents = new ArrayList<>();
        if (shop != null) {
            for (int i = 0; i < shop.getStorage().size(); i++) {
                ItemStack stack = shop.getStorage().getStack(i);
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
        for (Shop s : shops.values()) {
            list.add(s.toNbt(registryLookup));
        }
        nbt.put("shops", list);
        return nbt;
    }

    private static ShopManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ShopManager mgr = new ShopManager();
        NbtList list = nbt.getList("shops", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Shop shop = Shop.fromNbt(list.getCompound(i), registryLookup);
            shop.getStorage().setOnChanged(mgr::markDirty);
            mgr.shops.put(shop.getId(), shop);
        }
        return mgr;
    }
}
