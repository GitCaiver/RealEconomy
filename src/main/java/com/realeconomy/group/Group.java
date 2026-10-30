package com.realeconomy.group;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Representa un grupo de jugadores: nombre, siglas (tag), color, líder y miembros.
 * La cuenta/monedas del grupo se añaden en partes posteriores.
 */
public class Group {

    private final String id;
    private String name;
    private String tag;
    private Formatting color;
    private UUID leader;
    /** uuid -> último nombre conocido (para mostrar miembros desconectados). */
    private final Map<UUID, String> members = new LinkedHashMap<>();

    public Group(String id, String name, String tag, Formatting color, UUID leader, String leaderName) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.color = color;
        this.leader = leader;
        this.members.put(leader, leaderName);
    }

    private Group(String id) {
        this.id = id;
    }

    public static Group create(String name, String tag, Formatting color, UUID leader, String leaderName) {
        return new Group(UUID.randomUUID().toString(), name, tag, color, leader, leaderName);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
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

    public String getMemberName(UUID uuid) {
        return members.get(uuid);
    }

    /** Busca un miembro por nombre (sin distinguir mayúsculas). Devuelve null si no está. */
    public UUID getMemberByName(String name) {
        for (Map.Entry<UUID, String> e : members.entrySet()) {
            if (e.getValue() != null && e.getValue().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
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
        c.putString("name", name);
        c.putString("tag", tag);
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

    public static Group fromNbt(NbtCompound c) {
        Group g = new Group(c.getString("id"));
        g.name = c.getString("name");
        g.tag = c.getString("tag");
        Formatting f = Formatting.byName(c.getString("color"));
        g.color = (f == null || !f.isColor()) ? Formatting.WHITE : f;
        g.leader = c.getUuid("leader");
        NbtList memberList = c.getList("members", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < memberList.size(); i++) {
            NbtCompound mc = memberList.getCompound(i);
            g.members.put(mc.getUuid("uuid"), mc.getString("name"));
        }
        return g;
    }
}
