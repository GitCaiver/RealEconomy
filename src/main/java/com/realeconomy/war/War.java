package com.realeconomy.war;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Una guerra entre dos países por una provincia del defensor. La zona de guerra
 * son los chunks de esa provincia. La victoria se decide por jugadores vivos
 * (eliminación), por rendición o por tiempo. Al ganar el atacante, conquista la
 * provincia.
 */
public class War {

    public static final int SIDE_NONE = 0;
    public static final int SIDE_ATTACKER = 1;
    public static final int SIDE_DEFENDER = 2;

    private final String id;
    private final String attackerId;
    private final String defenderId;
    private final String provinceId;
    private final long start;
    private long end;
    private boolean ended;
    private String winnerId; // null mientras está activa; "" = empate/sin cambio

    private final Set<UUID> attackers = new LinkedHashSet<>();
    private final Set<UUID> defenders = new LinkedHashSet<>();
    private final Set<UUID> eliminated = new LinkedHashSet<>();
    /** Aldeanos de MCA alistados (uuid de entidad -> uuid del dueño), por bando. Parte 7. */
    private final Map<UUID, UUID> villagerAttackers = new LinkedHashMap<>();
    private final Map<UUID, UUID> villagerDefenders = new LinkedHashMap<>();

    public War(String id, String attackerId, String defenderId, String provinceId, long start, long end) {
        this.id = id;
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.provinceId = provinceId;
        this.start = start;
        this.end = end;
    }

    public static War create(String attackerId, String defenderId, String provinceId, long durationMs) {
        long now = System.currentTimeMillis();
        return new War(UUID.randomUUID().toString(), attackerId, defenderId, provinceId, now, now + durationMs);
    }

    public String getId() {
        return id;
    }

    public String getAttackerId() {
        return attackerId;
    }

    public String getDefenderId() {
        return defenderId;
    }

    public String getProvinceId() {
        return provinceId;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public boolean isEnded() {
        return ended;
    }

    public void setEnded(boolean ended) {
        this.ended = ended;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    public long remaining() {
        return Math.max(0, end - System.currentTimeMillis());
    }

    // ---- Participantes (jugadores) ----

    public Set<UUID> attackers() {
        return attackers;
    }

    public Set<UUID> defenders() {
        return defenders;
    }

    public Set<UUID> eliminated() {
        return eliminated;
    }

    private Map<UUID, UUID> villagerMap(int side) {
        return side == SIDE_ATTACKER ? villagerAttackers : villagerDefenders;
    }

    /** Alista un aldeano (con su dueño) en un bando. */
    public void enlistVillager(int side, UUID villager, UUID owner) {
        villagerMap(side).put(villager, owner);
    }

    /** Elimina un aldeano de la guerra (ambos bandos). Devuelve true si estaba. */
    public boolean removeVillager(UUID villager) {
        return villagerAttackers.remove(villager) != null | villagerDefenders.remove(villager) != null;
    }

    /** Bando del aldeano alistado, o SIDE_NONE. */
    public int villagerSide(UUID villager) {
        if (villagerAttackers.containsKey(villager)) {
            return SIDE_ATTACKER;
        }
        if (villagerDefenders.containsKey(villager)) {
            return SIDE_DEFENDER;
        }
        return SIDE_NONE;
    }

    public Set<UUID> villagerKeys(int side) {
        return villagerMap(side).keySet();
    }

    public int villagerCount(int side) {
        return villagerMap(side).size();
    }

    public long villagerCountForOwner(int side, UUID owner) {
        return villagerMap(side).values().stream().filter(owner::equals).count();
    }

    public boolean involves(String countryId) {
        return attackerId.equals(countryId) || defenderId.equals(countryId);
    }

    public int sideOfCountry(String countryId) {
        if (attackerId.equals(countryId)) {
            return SIDE_ATTACKER;
        }
        if (defenderId.equals(countryId)) {
            return SIDE_DEFENDER;
        }
        return SIDE_NONE;
    }

    public int sideOfPlayer(UUID uuid) {
        if (attackers.contains(uuid)) {
            return SIDE_ATTACKER;
        }
        if (defenders.contains(uuid)) {
            return SIDE_DEFENDER;
        }
        return SIDE_NONE;
    }

    public boolean isParticipant(UUID uuid) {
        return attackers.contains(uuid) || defenders.contains(uuid);
    }

    /** Participantes de un bando que aún no han sido eliminados. */
    public int aliveCount(int side) {
        Set<UUID> set = side == SIDE_ATTACKER ? attackers : defenders;
        int n = 0;
        for (UUID u : set) {
            if (!eliminated.contains(u)) {
                n++;
            }
        }
        return n;
    }

    public int enlistedCount(int side) {
        return (side == SIDE_ATTACKER ? attackers : defenders).size();
    }

    // ---- NBT ----

    private static void writeUuids(NbtCompound c, String key, Set<UUID> set) {
        NbtList list = new NbtList();
        for (UUID u : set) {
            NbtCompound e = new NbtCompound();
            e.putUuid("u", u);
            list.add(e);
        }
        c.put(key, list);
    }

    private static void readUuids(NbtCompound c, String key, Set<UUID> set) {
        NbtList list = c.getList(key, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            set.add(list.getCompound(i).getUuid("u"));
        }
    }

    private static void writeVillagers(NbtCompound c, String key, Map<UUID, UUID> map) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, UUID> e : map.entrySet()) {
            NbtCompound n = new NbtCompound();
            n.putUuid("v", e.getKey());
            n.putUuid("o", e.getValue());
            list.add(n);
        }
        c.put(key, list);
    }

    private static void readVillagers(NbtCompound c, String key, Map<UUID, UUID> map) {
        NbtList list = c.getList(key, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound n = list.getCompound(i);
            map.put(n.getUuid("v"), n.getUuid("o"));
        }
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putString("attacker", attackerId);
        c.putString("defender", defenderId);
        c.putString("province", provinceId);
        c.putLong("start", start);
        c.putLong("end", end);
        c.putBoolean("ended", ended);
        if (winnerId != null) {
            c.putString("winner", winnerId);
        }
        writeUuids(c, "atk", attackers);
        writeUuids(c, "def", defenders);
        writeUuids(c, "elim", eliminated);
        writeVillagers(c, "vatk", villagerAttackers);
        writeVillagers(c, "vdef", villagerDefenders);
        return c;
    }

    public static War fromNbt(NbtCompound c) {
        War w = new War(c.getString("id"), c.getString("attacker"), c.getString("defender"),
                c.getString("province"), c.getLong("start"), c.getLong("end"));
        w.ended = c.getBoolean("ended");
        if (c.contains("winner")) {
            w.winnerId = c.getString("winner");
        }
        readUuids(c, "atk", w.attackers);
        readUuids(c, "def", w.defenders);
        readUuids(c, "elim", w.eliminated);
        readVillagers(c, "vatk", w.villagerAttackers);
        readVillagers(c, "vdef", w.villagerDefenders);
        return w;
    }
}
