package com.realeconomy.group;

import com.realeconomy.RealEconomy;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fuente de verdad de los grupos. Se guarda en el mundo (PersistentState) y
 * refleja cada grupo en un equipo de scoreboard para pintar el nombre con
 * siglas y color de forma 100% vanilla (tab, placa y chat).
 */
public class GroupManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_groups";

    public static final PersistentState.Type<GroupManager> TYPE = new PersistentState.Type<>(
            GroupManager::new,
            GroupManager::fromNbt,
            null
    );

    /** groupId -> Group. */
    private final Map<String, Group> groups = new HashMap<>();
    /** playerUuid -> groupId. */
    private final Map<UUID, String> playerIndex = new HashMap<>();
    /** Invitaciones pendientes (en memoria): playerUuid -> set de groupId. */
    private final Map<UUID, Set<String>> invites = new HashMap<>();

    private MinecraftServer server;

    public GroupManager() {
    }

    public static GroupManager get(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        PersistentStateManager psm = world.getPersistentStateManager();
        GroupManager mgr = psm.getOrCreate(TYPE, STATE_ID);
        mgr.server = server;
        return mgr;
    }

    // ---- Consultas ----

    public Group getById(String id) {
        return groups.get(id);
    }

    public Group getByPlayer(UUID uuid) {
        String id = playerIndex.get(uuid);
        return id == null ? null : groups.get(id);
    }

    public Group getByTag(String tag) {
        String norm = normalizeTag(tag);
        for (Group g : groups.values()) {
            if (normalizeTag(g.getTag()).equals(norm)) {
                return g;
            }
        }
        return null;
    }

    public Collection<Group> getGroups() {
        return groups.values();
    }

    public boolean tagExists(String tag) {
        return getByTag(tag) != null;
    }

    public static String normalizeTag(String tag) {
        return tag == null ? "" : tag.toUpperCase(Locale.ROOT);
    }

    /** Valida formato de siglas: 2-4 letras A-Z. Devuelve null si es válido, o un mensaje de error. */
    public static String validateTag(String tag) {
        if (tag == null || !tag.matches("[A-Za-z]{2,4}")) {
            return "Las siglas deben tener entre 2 y 4 letras (sin números ni símbolos).";
        }
        return null;
    }

    // ---- Operaciones ----

    public Group createGroup(String name, String tag, Formatting color, ServerPlayerEntity leader) {
        Group g = Group.create(name, normalizeTag(tag), color, leader.getUuid(), leader.getNameForScoreboard());
        groups.put(g.getId(), g);
        playerIndex.put(leader.getUuid(), g.getId());
        syncTeam(g);
        addToTeam(g, leader.getNameForScoreboard());
        markDirty();
        return g;
    }

    public void disband(Group g) {
        removeTeam(g);
        for (UUID uuid : new HashSet<>(g.getMembers())) {
            playerIndex.remove(uuid);
            invites.remove(uuid);
        }
        // Limpia la cuenta del grupo y sus datos financieros (impuestos/sueldos).
        if (server != null) {
            com.realeconomy.economy.BalanceManager.get(server).clear(com.realeconomy.finance.FinanceManager.accountUuid(g));
            com.realeconomy.finance.FinanceManager.get(server).removeGroup(g.getId());
        }
        groups.remove(g.getId());
        markDirty();
    }

    public void addMember(Group g, UUID uuid, String name) {
        g.addMember(uuid, name);
        playerIndex.put(uuid, g.getId());
        clearInvites(uuid);
        addToTeam(g, name);
        markDirty();
    }

    public void removeMember(Group g, UUID uuid) {
        String name = g.getMemberName(uuid);
        g.removeMember(uuid);
        playerIndex.remove(uuid);
        if (name != null) {
            removeFromTeam(name);
        }
        markDirty();
    }

    public void setTag(Group g, String tag) {
        g.setTag(normalizeTag(tag));
        syncTeam(g);
        markDirty();
    }

    public void setColor(Group g, Formatting color) {
        g.setColor(color);
        syncTeam(g);
        markDirty();
    }

    public void setName(Group g, String name) {
        g.setName(name);
        syncTeam(g);
        markDirty();
    }

    public void setLeader(Group g, UUID uuid) {
        g.setLeader(uuid);
        markDirty();
    }

    // ---- Invitaciones (en memoria) ----

    public void invite(Group g, UUID target) {
        invites.computeIfAbsent(target, k -> new HashSet<>()).add(g.getId());
    }

    public boolean hasInvite(UUID player, Group g) {
        Set<String> set = invites.get(player);
        return set != null && set.contains(g.getId());
    }

    public Set<String> getInvites(UUID player) {
        return invites.getOrDefault(player, Set.of());
    }

    public void clearInvites(UUID player) {
        invites.remove(player);
    }

    public void removeInvite(UUID player, Group g) {
        Set<String> set = invites.get(player);
        if (set != null) {
            set.remove(g.getId());
            if (set.isEmpty()) {
                invites.remove(player);
            }
        }
    }

    // ---- Sincronización de equipos de scoreboard ----

    public void onPlayerJoin(ServerPlayerEntity player) {
        Group g = getByPlayer(player.getUuid());
        if (g == null) {
            return;
        }
        // Actualiza el nombre conocido por si cambió.
        g.setMemberName(player.getUuid(), player.getNameForScoreboard());
        syncTeam(g);
        addToTeam(g, player.getNameForScoreboard());
        markDirty();
    }

    private String teamName(Group g) {
        String raw = g.getId().replace("-", "");
        return "re_" + raw.substring(0, Math.min(13, raw.length()));
    }

    private void syncTeam(Group g) {
        if (server == null) {
            return;
        }
        Scoreboard sb = server.getScoreboard();
        String tn = teamName(g);
        Team team = sb.getTeam(tn);
        if (team == null) {
            team = sb.addTeam(tn);
        }
        team.setColor(g.getColor());
        team.setDisplayName(Text.literal(g.getName()));
        team.setSuffix(Text.literal(" [" + g.getTag() + "]").formatted(g.getColor()));
    }

    private void addToTeam(Group g, String playerName) {
        if (server == null || playerName == null) {
            return;
        }
        Scoreboard sb = server.getScoreboard();
        Team team = sb.getTeam(teamName(g));
        if (team != null) {
            sb.addScoreHolderToTeam(playerName, team);
        }
    }

    private void removeFromTeam(String playerName) {
        if (server == null || playerName == null) {
            return;
        }
        Scoreboard sb = server.getScoreboard();
        Team team = sb.getScoreHolderTeam(playerName);
        if (team != null && team.getName().startsWith("re_")) {
            sb.removeScoreHolderFromTeam(playerName, team);
        }
    }

    private void removeTeam(Group g) {
        if (server == null) {
            return;
        }
        Scoreboard sb = server.getScoreboard();
        Team team = sb.getTeam(teamName(g));
        if (team != null) {
            sb.removeTeam(team);
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Group g : groups.values()) {
            list.add(g.toNbt());
        }
        nbt.put("groups", list);
        return nbt;
    }

    private static GroupManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        GroupManager mgr = new GroupManager();
        NbtList list = nbt.getList("groups", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Group g = Group.fromNbt(list.getCompound(i));
            mgr.groups.put(g.getId(), g);
            for (UUID member : g.getMembers()) {
                mgr.playerIndex.put(member, g.getId());
            }
        }
        return mgr;
    }
}
