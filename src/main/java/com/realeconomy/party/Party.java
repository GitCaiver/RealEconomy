package com.realeconomy.party;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un partido político dentro de un país (parte 1 de la segunda tanda). Tiene
 * nombre, color, un fundador (líder del partido) y miembros. Un jugador solo
 * puede estar en un partido de su propio país. El partido no afecta a cómo se
 * muestra el jugador en el tab/placa/chat: ahí siempre salen las siglas del país.
 */
public class Party {

    private final String id;
    private final String countryId;
    private String name;
    private Formatting color;
    private UUID leader;
    /** uuid -> último nombre conocido. */
    private final Map<UUID, String> members = new LinkedHashMap<>();

    private Party(String id, String countryId) {
        this.id = id;
        this.countryId = countryId;
    }

    public Party(String id, String countryId, String name, Formatting color, UUID leader, String leaderName) {
        this.id = id;
        this.countryId = countryId;
        this.name = name;
        this.color = color;
        this.leader = leader;
        this.members.put(leader, leaderName);
    }

    public static Party create(String countryId, String name, Formatting color, UUID leader, String leaderName) {
        return new Party(UUID.randomUUID().toString(), countryId, name, color, leader, leaderName);
    }

    public String getId() {
        return id;
    }

    public String getCountryId() {
        return countryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Formatting getColor() {
        return color;
    }

    public void setColor(Formatting color) {
        this.color = color;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public Set<UUID> getMembers() {
        return members.keySet();
    }

    public Map<UUID, String> getMemberNames() {
        return members;
    }

    public void addMember(UUID uuid, String name) {
        members.put(uuid, name);
    }

    public void setMemberName(UUID uuid, String name) {
        if (members.containsKey(uuid)) {
            members.put(uuid, name);
        }
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public int size() {
        return members.size();
    }

    // ---- NBT ----

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putString("country", countryId);
        c.putString("name", name);
        c.putString("color", color.getName());
        c.putUuid("leader", leader);
        NbtList memberList = new NbtList();
        for (Map.Entry<UUID, String> e : members.entrySet()) {
            NbtCompound mc = new NbtCompound();
            mc.putUuid("uuid", e.getKey());
            mc.putString("name", e.getValue() == null ? "" : e.getValue());
            memberList.add(mc);
        }
        c.put("members", memberList);
        return c;
    }

    public static Party fromNbt(NbtCompound c) {
        Party p = new Party(c.getString("id"), c.getString("country"));
        p.name = c.getString("name");
        Formatting f = Formatting.byName(c.getString("color"));
        p.color = (f == null || !f.isColor()) ? Formatting.WHITE : f;
        p.leader = c.getUuid("leader");
        NbtList memberList = c.getList("members", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < memberList.size(); i++) {
            NbtCompound mc = memberList.getCompound(i);
            p.members.put(mc.getUuid("uuid"), mc.getString("name"));
        }
        return p;
    }
}
