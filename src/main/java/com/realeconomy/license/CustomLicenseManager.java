package com.realeconomy.license;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Licencias personalizadas (parte 5, 3ª tanda): definiciones por país que el gobierno crea,
 * y las licencias que cada jugador posee (con su caducidad). A diferencia de las licencias
 * consumibles de tienda/subasta ({@link com.realeconomy.townhall.LicenseManager}), estas se
 * poseen mientras estén vigentes y habilitan acciones concretas.
 *
 * <p>Regla de aplicación: una acción solo se bloquea si el país del jugador ha definido al
 * menos una licencia para ese permiso. Si el país no define ninguna, la acción sigue libre
 * (compatibilidad con el comportamiento anterior).</p>
 */
public class CustomLicenseManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_customlicenses";

    public static final PersistentState.Type<CustomLicenseManager> TYPE = new PersistentState.Type<>(
            CustomLicenseManager::new,
            CustomLicenseManager::fromNbt,
            null
    );

    /** countryId (UUID string) -> definiciones de licencia. */
    private final Map<String, List<License>> byCountry = new HashMap<>();
    /** defId -> definición (índice rápido). */
    private final Map<String, License> byId = new HashMap<>();
    /** playerUuid -> (defId -> caducidad en ms; 0 = permanente). */
    private final Map<UUID, Map<String, Long>> holdings = new HashMap<>();

    public static CustomLicenseManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    // ---- Definiciones ----

    /** Lista (copia) de las licencias definidas por un país. */
    public List<License> defs(String countryId) {
        List<License> list = byCountry.get(countryId);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public License defById(String defId) {
        return byId.get(defId);
    }

    /** Busca una definición de un país por nombre (insensible a mayúsculas). Null si no existe. */
    public License defByName(String countryId, String name) {
        List<License> list = byCountry.get(countryId);
        if (list == null) {
            return null;
        }
        for (License l : list) {
            if (l.getName().equalsIgnoreCase(name)) {
                return l;
            }
        }
        return null;
    }

    public void define(License license) {
        byCountry.computeIfAbsent(license.getCountryId(), k -> new ArrayList<>()).add(license);
        byId.put(license.getId(), license);
        markDirty();
    }

    /** Elimina una definición y las licencias que los jugadores tuvieran de ella. */
    public boolean removeDef(String countryId, String name) {
        License def = defByName(countryId, name);
        if (def == null) {
            return false;
        }
        List<License> list = byCountry.get(countryId);
        if (list != null) {
            list.remove(def);
            if (list.isEmpty()) {
                byCountry.remove(countryId);
            }
        }
        byId.remove(def.getId());
        for (Map<String, Long> held : holdings.values()) {
            held.remove(def.getId());
        }
        markDirty();
        return true;
    }

    /** Borra todas las definiciones de un país (al disolverlo). */
    public void removeCountry(String countryId) {
        List<License> list = byCountry.remove(countryId);
        if (list != null) {
            for (License l : list) {
                byId.remove(l.getId());
                for (Map<String, Long> held : holdings.values()) {
                    held.remove(l.getId());
                }
            }
            markDirty();
        }
    }

    // ---- Posesión por jugador ----

    /** Registra que un jugador adquiere una licencia (calcula la caducidad desde ahora). */
    public void grant(UUID player, License def, long now) {
        long expiry = def.isPermanent() ? 0L : now + def.getDurationMillis();
        holdings.computeIfAbsent(player, k -> new LinkedHashMap<>()).put(def.getId(), expiry);
        markDirty();
    }

    /** Caducidad de la licencia que el jugador posee (0 = permanente, -1 = no la tiene). */
    public long heldExpiry(UUID player, String defId) {
        Map<String, Long> held = holdings.get(player);
        if (held == null || !held.containsKey(defId)) {
            return -1L;
        }
        return held.get(defId);
    }

    /** ¿El jugador posee esa licencia y sigue vigente? */
    public boolean holdsActive(UUID player, String defId, long now) {
        long exp = heldExpiry(player, defId);
        if (exp < 0) {
            return false;
        }
        return exp == 0 || exp > now;
    }

    // ---- Lógica de permisos ----

    /** ¿El país exige una licencia (ha definido alguna) para ese permiso? */
    public boolean requires(String countryId, LicensePermission perm) {
        List<License> list = byCountry.get(countryId);
        if (list == null) {
            return false;
        }
        for (License l : list) {
            if (l.getPermission() == perm) {
                return true;
            }
        }
        return false;
    }

    /** ¿El jugador posee una licencia vigente de su país que otorgue ese permiso? */
    public boolean hasActive(UUID player, String countryId, LicensePermission perm, long now) {
        Map<String, Long> held = holdings.get(player);
        if (held == null) {
            return false;
        }
        List<License> list = byCountry.get(countryId);
        if (list == null) {
            return false;
        }
        for (License l : list) {
            if (l.getPermission() == perm && holdsActive(player, l.getId(), now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comprueba si el jugador puede realizar una acción que requiere {@code perm}. Si su país
     * exige licencia para ese permiso y no la tiene vigente, devuelve false (acción bloqueada).
     * Sin país o sin licencias definidas para ese permiso, devuelve true.
     */
    public boolean permits(String countryId, UUID player, LicensePermission perm) {
        if (countryId == null || !requires(countryId, perm)) {
            return true;
        }
        return hasActive(player, countryId, perm, System.currentTimeMillis());
    }

    // ---- Persistencia ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList defs = new NbtList();
        for (List<License> list : byCountry.values()) {
            for (License l : list) {
                defs.add(l.toNbt());
            }
        }
        nbt.put("definitions", defs);

        NbtList holders = new NbtList();
        for (Map.Entry<UUID, Map<String, Long>> e : holdings.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            NbtCompound c = new NbtCompound();
            c.putUuid("player", e.getKey());
            NbtList owned = new NbtList();
            for (Map.Entry<String, Long> h : e.getValue().entrySet()) {
                NbtCompound o = new NbtCompound();
                o.putString("def", h.getKey());
                o.putLong("expiry", h.getValue());
                owned.add(o);
            }
            c.put("owned", owned);
            holders.add(c);
        }
        nbt.put("holdings", holders);
        return nbt;
    }

    private static CustomLicenseManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CustomLicenseManager mgr = new CustomLicenseManager();
        NbtList defs = nbt.getList("definitions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < defs.size(); i++) {
            License l = License.fromNbt(defs.getCompound(i));
            mgr.byCountry.computeIfAbsent(l.getCountryId(), k -> new ArrayList<>()).add(l);
            mgr.byId.put(l.getId(), l);
        }
        NbtList holders = nbt.getList("holdings", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < holders.size(); i++) {
            NbtCompound c = holders.getCompound(i);
            UUID player = c.getUuid("player");
            Map<String, Long> owned = new LinkedHashMap<>();
            NbtList ownedList = c.getList("owned", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < ownedList.size(); j++) {
                NbtCompound o = ownedList.getCompound(j);
                owned.put(o.getString("def"), o.getLong("expiry"));
            }
            if (!owned.isEmpty()) {
                mgr.holdings.put(player, owned);
            }
        }
        return mgr;
    }
}
