package com.realeconomy.territory;

import com.realeconomy.RealEconomy;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Territorios: posesión de chunks por país (y provincia) en cada dimensión.
 * Fuente de verdad guardada en el mundo. Lo consultan los trabajos (una zona de
 * trabajo debe estar en territorio propio o sin dueño) y las guerras (conquista).
 *
 * <p>No protege bloques de jugadores por sí mismo: solo modela la propiedad del
 * terreno. Funciona con cualquier terreno, lo genere el mod que lo genere
 * (Towns and Towers incluido): solo depende de coordenadas de chunk.
 */
public class TerritoryManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_territory";

    public static final PersistentState.Type<TerritoryManager> TYPE = new PersistentState.Type<>(
            TerritoryManager::new,
            TerritoryManager::fromNbt,
            null
    );

    /** dimensionId -> (chunkPosLong -> Claim). */
    private final Map<String, Map<Long, Claim>> claims = new HashMap<>();
    /** provinceId -> Province. */
    private final Map<String, Province> provinces = new HashMap<>();
    /** provinceId -> núcleo territorial (baluarte) que la ancla. */
    private final Map<String, TerritoryCore> cores = new HashMap<>();

    public TerritoryManager() {
    }

    public static TerritoryManager get(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    private static String dimId(ServerWorld world) {
        return world.getRegistryKey().getValue().toString();
    }

    // ---- Consultas ----

    public Claim getClaim(ServerWorld world, ChunkPos cp) {
        Map<Long, Claim> m = claims.get(dimId(world));
        return m == null ? null : m.get(cp.toLong());
    }

    public Claim getClaim(ServerWorld world, BlockPos pos) {
        return getClaim(world, new ChunkPos(pos));
    }

    public boolean isClaimed(ServerWorld world, ChunkPos cp) {
        return getClaim(world, cp) != null;
    }

    /** País dueño del chunk donde está la posición, o null si no tiene dueño. */
    public String ownerCountry(ServerWorld world, BlockPos pos) {
        Claim c = getClaim(world, pos);
        return c == null ? null : c.countryId();
    }

    /** Provincia del chunk donde está la posición, o null (sin dueño o país sin provincia). */
    public String provinceAt(ServerWorld world, BlockPos pos) {
        Claim c = getClaim(world, pos);
        return c == null ? null : c.provinceId();
    }

    /**
     * ¿Puede un empleado de {@code employerCountryId} trabajar (romper/plantar) en
     * esta posición? Sí si el terreno no tiene dueño o es del propio país. Nunca en
     * territorio de otro país.
     */
    public boolean canWorkAt(ServerWorld world, BlockPos pos, String employerCountryId) {
        String owner = ownerCountry(world, pos);
        return owner == null || owner.equals(employerCountryId);
    }

    public int claimCount(String countryId) {
        int n = 0;
        for (Map<Long, Claim> m : claims.values()) {
            for (Claim c : m.values()) {
                if (c.countryId().equals(countryId)) {
                    n++;
                }
            }
        }
        return n;
    }

    public int claimCountOfProvince(String provinceId) {
        int n = 0;
        for (Map<Long, Claim> m : claims.values()) {
            for (Claim c : m.values()) {
                if (provinceId.equals(c.provinceId())) {
                    n++;
                }
            }
        }
        return n;
    }

    // ---- Reclamar / abandonar ----

    public void claim(ServerWorld world, ChunkPos cp, String countryId, String provinceId) {
        claims.computeIfAbsent(dimId(world), k -> new HashMap<>()).put(cp.toLong(), new Claim(countryId, provinceId));
        markDirty();
    }

    public void unclaim(ServerWorld world, ChunkPos cp) {
        Map<Long, Claim> m = claims.get(dimId(world));
        if (m != null && m.remove(cp.toLong()) != null) {
            markDirty();
        }
    }

    /** Conquista: transfiere este chunk a otro país (pierde su provincia anterior). */
    public void setClaimCountry(ServerWorld world, ChunkPos cp, String newCountryId) {
        claims.computeIfAbsent(dimId(world), k -> new HashMap<>()).put(cp.toLong(), new Claim(newCountryId, null));
        markDirty();
    }

    // ---- Provincias ----

    public Province getProvince(String provinceId) {
        return provinceId == null ? null : provinces.get(provinceId);
    }

    public Province getProvinceByName(String countryId, String name) {
        if (name == null) {
            return null;
        }
        String norm = name.toLowerCase(Locale.ROOT);
        for (Province p : provinces.values()) {
            if (p.getCountryId().equals(countryId) && p.getName().toLowerCase(Locale.ROOT).equals(norm)) {
                return p;
            }
        }
        return null;
    }

    public List<Province> provincesOf(String countryId) {
        List<Province> list = new ArrayList<>();
        for (Province p : provinces.values()) {
            if (p.getCountryId().equals(countryId)) {
                list.add(p);
            }
        }
        return list;
    }

    /** Crea una provincia. Devuelve null si ya existe una con ese nombre en el país. */
    public Province createProvince(String countryId, String name, net.minecraft.util.Formatting color) {
        if (getProvinceByName(countryId, name) != null) {
            return null;
        }
        Province p = Province.create(countryId, name, color);
        provinces.put(p.getId(), p);
        markDirty();
        return p;
    }

    /** Borra una provincia; sus chunks quedan en el país sin provincia. */
    public void removeProvince(String provinceId) {
        if (provinces.remove(provinceId) == null) {
            return;
        }
        cores.remove(provinceId);
        for (Map<Long, Claim> m : claims.values()) {
            for (Map.Entry<Long, Claim> e : m.entrySet()) {
                if (provinceId.equals(e.getValue().provinceId())) {
                    e.setValue(new Claim(e.getValue().countryId(), null));
                }
            }
        }
        markDirty();
    }

    /**
     * Conquista de una provincia entera: la provincia y todos sus chunks pasan al
     * país conquistador conservando el nombre de la provincia.
     */
    public void transferProvince(String provinceId, String newCountryId) {
        Province p = provinces.get(provinceId);
        if (p == null) {
            return;
        }
        p.setCountryId(newCountryId);
        for (Map<Long, Claim> m : claims.values()) {
            for (Map.Entry<Long, Claim> e : m.entrySet()) {
                if (provinceId.equals(e.getValue().provinceId())) {
                    e.setValue(new Claim(newCountryId, provinceId));
                }
            }
        }
        // El núcleo (si sigue en pie) pasa al conquistador junto con la provincia.
        TerritoryCore core = cores.get(provinceId);
        if (core != null) {
            cores.put(provinceId, new TerritoryCore(core.dim(), core.posLong(), newCountryId, provinceId));
        }
        markDirty();
    }

    // ---- Núcleos territoriales (baluartes) ----

    /** Registra un núcleo para una provincia (sustituye al anterior de esa provincia). */
    public void placeCore(ServerWorld world, BlockPos pos, String countryId, String provinceId) {
        cores.put(provinceId, new TerritoryCore(dimId(world), pos.asLong(), countryId, provinceId));
        markDirty();
    }

    /** Núcleo de una provincia, o null si no tiene. */
    public TerritoryCore coreOfProvince(String provinceId) {
        return provinceId == null ? null : cores.get(provinceId);
    }

    /** Núcleo que ocupa exactamente esta posición de bloque, o null. */
    public TerritoryCore coreAt(ServerWorld world, BlockPos pos) {
        String dim = dimId(world);
        long key = pos.asLong();
        for (TerritoryCore c : cores.values()) {
            if (c.dim().equals(dim) && c.posLong() == key) {
                return c;
            }
        }
        return null;
    }

    /** Quita el registro del núcleo de una provincia (no toca el bloque del mundo). */
    public TerritoryCore removeCore(String provinceId) {
        TerritoryCore c = cores.remove(provinceId);
        if (c != null) {
            markDirty();
        }
        return c;
    }

    /** Al disolver un país: libera sus chunks y borra sus provincias y núcleos. */
    public void removeCountry(String countryId) {
        boolean changed = false;
        for (Map<Long, Claim> m : claims.values()) {
            changed |= m.values().removeIf(c -> c.countryId().equals(countryId));
        }
        changed |= provinces.values().removeIf(p -> p.getCountryId().equals(countryId));
        changed |= cores.values().removeIf(c -> c.countryId().equals(countryId));
        if (changed) {
            markDirty();
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList claimList = new NbtList();
        for (Map.Entry<String, Map<Long, Claim>> dim : claims.entrySet()) {
            for (Map.Entry<Long, Claim> e : dim.getValue().entrySet()) {
                NbtCompound c = new NbtCompound();
                c.putString("dim", dim.getKey());
                c.putLong("pos", e.getKey());
                c.putString("country", e.getValue().countryId());
                if (e.getValue().provinceId() != null) {
                    c.putString("province", e.getValue().provinceId());
                }
                claimList.add(c);
            }
        }
        nbt.put("claims", claimList);
        NbtList provList = new NbtList();
        for (Province p : provinces.values()) {
            provList.add(p.toNbt());
        }
        nbt.put("provinces", provList);
        NbtList coreList = new NbtList();
        for (TerritoryCore c : cores.values()) {
            coreList.add(c.toNbt());
        }
        nbt.put("cores", coreList);
        return nbt;
    }

    private static TerritoryManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        TerritoryManager mgr = new TerritoryManager();
        NbtList claimList = nbt.getList("claims", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < claimList.size(); i++) {
            NbtCompound c = claimList.getCompound(i);
            String province = c.contains("province") ? c.getString("province") : null;
            mgr.claims.computeIfAbsent(c.getString("dim"), k -> new HashMap<>())
                    .put(c.getLong("pos"), new Claim(c.getString("country"), province));
        }
        NbtList provList = nbt.getList("provinces", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < provList.size(); i++) {
            Province p = Province.fromNbt(provList.getCompound(i));
            mgr.provinces.put(p.getId(), p);
        }
        NbtList coreList = nbt.getList("cores", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < coreList.size(); i++) {
            TerritoryCore c = TerritoryCore.fromNbt(coreList.getCompound(i));
            mgr.cores.put(c.provinceId(), c);
        }
        return mgr;
    }
}
