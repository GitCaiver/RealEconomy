package com.realeconomy.buyer;

import com.realeconomy.RealEconomy;
import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.MonetaryManager;
import net.minecraft.item.Item;
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

/**
 * Estado de oferta/demanda de los compradores (parte 4): un multiplicador de
 * precio por categoría (global en el servidor) que baja al vender y se recupera
 * hacia la base con el tiempo real. Los precios base están en esmeraldas; cada
 * país paga en su moneda usando el valor actual (parte 3).
 */
public class BuyerMarketManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_buyers";

    public static final PersistentState.Type<BuyerMarketManager> TYPE = new PersistentState.Type<>(
            BuyerMarketManager::new,
            BuyerMarketManager::fromNbt,
            null
    );

    private static final class Cat {
        double multiplier = 1.0;
        long last = System.currentTimeMillis();
    }

    private final Map<String, Cat> byCategory = new HashMap<>();

    public static BuyerMarketManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    /** Multiplicador actual de una categoría (aplica la recuperación por tiempo). */
    public double multiplier(String category) {
        if (!ModConfig.get().buyersDynamicPricing) {
            return 1.0;
        }
        Cat c = byCategory.computeIfAbsent(category, k -> new Cat());
        applyRecovery(c);
        return c.multiplier;
    }

    private void applyRecovery(Cat c) {
        long now = System.currentTimeMillis();
        if (c.multiplier < 1.0) {
            double secs = (now - c.last) / 1000.0;
            double rec = ModConfig.get().buyerRecoveryPerSecond * secs;
            if (rec > 0) {
                c.multiplier = Math.min(1.0, c.multiplier + rec);
                markDirty();
            }
        }
        c.last = now;
    }

    /** Registra una venta: baja el multiplicador de la categoría según las unidades. */
    public void recordSale(String category, int units) {
        if (!ModConfig.get().buyersDynamicPricing || units <= 0) {
            return;
        }
        Cat c = byCategory.computeIfAbsent(category, k -> new Cat());
        applyRecovery(c);
        double drop = ModConfig.get().buyerDropPerUnit * units;
        c.multiplier = Math.max(ModConfig.get().buyerMinMultiplier, c.multiplier - drop);
        c.last = System.currentTimeMillis();
        markDirty();
    }

    /** Precio en esmeraldas de un item (base * multiplicador de su categoría), o -1 si no es comprable. */
    public double emeraldPrice(Item item) {
        String cat = BuyerCategories.categoryOf(item);
        if (cat == null) {
            return -1.0;
        }
        return BuyerCategories.basePrice(item, cat) * multiplier(cat);
    }

    /** Precio en esmeraldas (representativo) de una categoría. */
    public double categoryEmeraldPrice(String category) {
        return BuyerCategories.categoryBasePrice(category) * multiplier(category);
    }

    /** Convierte un precio en esmeraldas a monedas del país (mínimo 1 si el precio es positivo). */
    public static long toCoins(MinecraftServer server, String currencyId, double emeralds) {
        if (emeralds <= 0) {
            return 0L;
        }
        long coins = MonetaryManager.get(server).coinsForEmeralds(currencyId, emeralds);
        return Math.max(1L, coins);
    }

    /** Precio por unidad en monedas del país para un item concreto, o -1 si no es comprable. */
    public long coinPrice(MinecraftServer server, String currencyId, Item item) {
        double em = emeraldPrice(item);
        if (em < 0) {
            return -1L;
        }
        return toCoins(server, currencyId, em);
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<String, Cat> e : byCategory.entrySet()) {
            Cat c = e.getValue();
            if (c.multiplier >= 1.0) {
                continue; // en la base: no hace falta guardarlo
            }
            NbtCompound cc = new NbtCompound();
            cc.putString("cat", e.getKey());
            cc.putDouble("mult", c.multiplier);
            cc.putLong("last", c.last);
            list.add(cc);
        }
        nbt.put("categories", list);
        return nbt;
    }

    private static BuyerMarketManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        BuyerMarketManager mgr = new BuyerMarketManager();
        NbtList list = nbt.getList("categories", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound cc = list.getCompound(i);
            Cat c = new Cat();
            c.multiplier = cc.getDouble("mult");
            c.last = cc.getLong("last");
            mgr.byCategory.put(cc.getString("cat"), c);
        }
        return mgr;
    }
}
