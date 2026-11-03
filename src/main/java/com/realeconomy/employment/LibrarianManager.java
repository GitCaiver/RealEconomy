package com.realeconomy.employment;

import com.realeconomy.RealEconomy;
import com.realeconomy.config.ModConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stock de libros encantados de cada librero (aldeano de MCA empleado como librero).
 * Cada librero elige al azar un número configurable de libros de la lista del config,
 * con unidades limitadas por ciclo, y el stock rota cada {@code librarianRotationHours}
 * (tiempo real; se guarda cuándo toca la siguiente rotación aunque se reinicie).
 */
public class LibrarianManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_librarian";

    public static final PersistentState.Type<LibrarianManager> TYPE = new PersistentState.Type<>(
            LibrarianManager::new,
            LibrarianManager::fromNbt,
            null
    );

    /** Una entrada de stock: encantamiento, nivel, precio (esmeraldas) y unidades restantes. */
    public static final class StockEntry {
        public final String enchant;
        public final int level;
        public final double priceEmeralds;
        public int units;

        public StockEntry(String enchant, int level, double priceEmeralds, int units) {
            this.enchant = enchant;
            this.level = level;
            this.priceEmeralds = priceEmeralds;
            this.units = units;
        }
    }

    private static final class Librarian {
        long nextRotation;
        final List<StockEntry> stock = new ArrayList<>();
    }

    private final Map<UUID, Librarian> byVillager = new HashMap<>();

    public LibrarianManager() {
    }

    public static LibrarianManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    /** Devuelve el stock actual del librero, rotándolo si toca (o si no tenía). */
    public List<StockEntry> stock(UUID villager) {
        Librarian lib = byVillager.computeIfAbsent(villager, k -> new Librarian());
        long now = System.currentTimeMillis();
        if (lib.stock.isEmpty() || now >= lib.nextRotation) {
            rotate(lib, now);
            markDirty();
        }
        return lib.stock;
    }

    private void rotate(Librarian lib, long now) {
        lib.stock.clear();
        List<ModConfig.BookEntry> pool = new ArrayList<>(ModConfig.get().librarianBooks);
        Collections.shuffle(pool);
        int n = Math.min(Math.max(1, ModConfig.get().librarianBooksPerCycle), pool.size());
        int units = Math.max(1, ModConfig.get().librarianUnitsPerBook);
        for (int i = 0; i < n; i++) {
            ModConfig.BookEntry b = pool.get(i);
            if (b != null && b.enchant != null) {
                lib.stock.add(new StockEntry(b.enchant, b.level, b.price, units));
            }
        }
        long hours = Math.max(1, ModConfig.get().librarianRotationHours);
        lib.nextRotation = now + hours * 3_600_000L;
    }

    public long nextRotation(UUID villager) {
        Librarian lib = byVillager.get(villager);
        return lib == null ? 0 : lib.nextRotation;
    }

    public void remove(UUID villager) {
        if (byVillager.remove(villager) != null) {
            markDirty();
        }
    }

    // ---- Construcción del libro encantado ----

    /** Construye el ItemStack del libro encantado para una entrada, o null si el encantamiento no existe. */
    public static ItemStack buildBook(MinecraftServer server, String enchantId, int level) {
        RegistryEntry<Enchantment> entry = enchantEntry(server, enchantId);
        if (entry == null) {
            return null;
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        b.add(entry, Math.max(1, level));
        book.set(DataComponentTypes.STORED_ENCHANTMENTS, b.build());
        return book;
    }

    private static RegistryEntry<Enchantment> enchantEntry(MinecraftServer server, String enchantId) {
        try {
            RegistryWrapper.Impl<Enchantment> reg = server.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
            RegistryKey<Enchantment> key = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(enchantId));
            Optional<RegistryEntry.Reference<Enchantment>> e = reg.getOptional(key);
            return e.orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, Librarian> e : byVillager.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("villager", e.getKey());
            c.putLong("next", e.getValue().nextRotation);
            NbtList stock = new NbtList();
            for (StockEntry s : e.getValue().stock) {
                NbtCompound sc = new NbtCompound();
                sc.putString("enchant", s.enchant);
                sc.putInt("level", s.level);
                sc.putDouble("price", s.priceEmeralds);
                sc.putInt("units", s.units);
                stock.add(sc);
            }
            c.put("stock", stock);
            list.add(c);
        }
        nbt.put("librarians", list);
        return nbt;
    }

    private static LibrarianManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        LibrarianManager mgr = new LibrarianManager();
        NbtList list = nbt.getList("librarians", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            Librarian lib = new Librarian();
            lib.nextRotation = c.getLong("next");
            NbtList stock = c.getList("stock", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < stock.size(); j++) {
                NbtCompound sc = stock.getCompound(j);
                lib.stock.add(new StockEntry(sc.getString("enchant"), sc.getInt("level"),
                        sc.getDouble("price"), sc.getInt("units")));
            }
            mgr.byVillager.put(c.getUuid("villager"), lib);
        }
        return mgr;
    }
}
