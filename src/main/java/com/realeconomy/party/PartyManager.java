package com.realeconomy.party;

import com.realeconomy.RealEconomy;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fuente de verdad de los partidos políticos. Se guarda en el mundo
 * (PersistentState). Cada partido pertenece a un país (id del {@code Group}); un
 * jugador solo puede estar en un partido a la vez, y de su propio país.
 */
public class PartyManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_parties";

    public static final PersistentState.Type<PartyManager> TYPE = new PersistentState.Type<>(
            PartyManager::new,
            PartyManager::fromNbt,
            null
    );

    /** partyId -> Party. */
    private final Map<String, Party> parties = new HashMap<>();
    /** playerUuid -> partyId. */
    private final Map<UUID, String> playerIndex = new HashMap<>();

    public static PartyManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    // ---- Consultas ----

    public Party getById(String id) {
        return parties.get(id);
    }

    public Party getByPlayer(UUID uuid) {
        String id = playerIndex.get(uuid);
        return id == null ? null : parties.get(id);
    }

    /** Partidos de un país concreto. */
    public List<Party> partiesOf(String countryId) {
        List<Party> out = new ArrayList<>();
        for (Party p : parties.values()) {
            if (p.getCountryId().equals(countryId)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Busca un partido por nombre dentro de un país (sin distinguir mayúsculas). */
    public Party getByNameInCountry(String countryId, String name) {
        for (Party p : parties.values()) {
            if (p.getCountryId().equals(countryId) && p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    // ---- Operaciones ----

    public Party createParty(String countryId, String name, Formatting color, UUID leader, String leaderName) {
        Party p = Party.create(countryId, name, color, leader, leaderName);
        parties.put(p.getId(), p);
        playerIndex.put(leader, p.getId());
        markDirty();
        return p;
    }

    public void joinParty(Party p, UUID uuid, String name) {
        p.addMember(uuid, name);
        playerIndex.put(uuid, p.getId());
        markDirty();
    }

    /** Saca al jugador del partido en el que esté. Devuelve el partido, o null si no estaba en ninguno. */
    public Party leaveParty(UUID uuid) {
        Party p = getByPlayer(uuid);
        if (p == null) {
            return null;
        }
        p.removeMember(uuid);
        playerIndex.remove(uuid);
        // Si el que se va era el líder y quedan miembros, pasa el liderazgo al siguiente.
        if (p.isLeader(uuid) && !p.getMembers().isEmpty()) {
            p.setLeader(p.getMembers().iterator().next());
        }
        if (p.getMembers().isEmpty()) {
            parties.remove(p.getId());
        }
        markDirty();
        return p;
    }

    public void disband(Party p) {
        for (UUID uuid : p.getMembers()) {
            playerIndex.remove(uuid);
        }
        parties.remove(p.getId());
        markDirty();
    }

    /** Elimina todos los partidos de un país (al disolverse el país). */
    public void removeByCountry(String countryId) {
        boolean changed = false;
        for (Party p : new ArrayList<>(parties.values())) {
            if (p.getCountryId().equals(countryId)) {
                for (UUID uuid : p.getMembers()) {
                    playerIndex.remove(uuid);
                }
                parties.remove(p.getId());
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    public void setMemberName(UUID uuid, String name) {
        Party p = getByPlayer(uuid);
        if (p != null) {
            p.setMemberName(uuid, name);
            markDirty();
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Party p : parties.values()) {
            list.add(p.toNbt());
        }
        nbt.put("parties", list);
        return nbt;
    }

    private static PartyManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        PartyManager mgr = new PartyManager();
        NbtList list = nbt.getList("parties", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Party p = Party.fromNbt(list.getCompound(i));
            mgr.parties.put(p.getId(), p);
            for (UUID member : p.getMembers()) {
                mgr.playerIndex.put(member, p.getId());
            }
        }
        return mgr;
    }
}
