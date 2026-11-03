package com.realeconomy.war;

import com.realeconomy.RealEconomy;
import com.realeconomy.config.ModConfig;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.territory.TerritoryManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Guerras entre países. Fuente de verdad en el mundo. Una guerra contesta una
 * provincia del defensor; la zona de guerra son los chunks de esa provincia.
 * La victoria se decide por eliminación de jugadores, rendición o tiempo. Al
 * ganar el atacante, conquista la provincia (vía {@link TerritoryManager}).
 */
public class WarManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_wars";

    public static final PersistentState.Type<WarManager> TYPE = new PersistentState.Type<>(
            WarManager::new,
            WarManager::fromNbt,
            null
    );

    private final List<War> active = new ArrayList<>();
    private final List<War> history = new ArrayList<>();
    private long lastTick;

    public WarManager() {
    }

    public static WarManager get(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    public List<War> activeWars() {
        return active;
    }

    public List<War> history() {
        return history;
    }

    public List<War> warsInvolving(String countryId) {
        List<War> list = new ArrayList<>();
        for (War w : active) {
            if (w.involves(countryId)) {
                list.add(w);
            }
        }
        return list;
    }

    /** ¿Hay ya una guerra activa del mismo atacante al mismo defensor por la misma provincia? */
    public boolean existsDuplicate(String attackerId, String defenderId, String provinceId) {
        for (War w : active) {
            if (w.getAttackerId().equals(attackerId) && w.getDefenderId().equals(defenderId)
                    && w.getProvinceId().equals(provinceId)) {
                return true;
            }
        }
        return false;
    }

    // ---- Declarar ----

    public War declare(MinecraftServer server, Group attacker, Group defender, String provinceId, long durationMs) {
        War w = War.create(attacker.getId(), defender.getId(), provinceId, durationMs);
        // Alista automáticamente a los miembros de cada país.
        w.attackers().addAll(attacker.getMembers());
        w.defenders().addAll(defender.getMembers());
        active.add(w);
        markDirty();
        announce(server, w, Text.literal("¡GUERRA! " + attacker.getName() + " [" + attacker.getTag()
                        + "] ataca la provincia de " + defender.getName() + " [" + defender.getTag() + "].")
                .formatted(Formatting.RED));
        return w;
    }

    // ---- Zona de guerra ----

    /** ¿Está la posición dentro de la zona de guerra (chunks de la provincia contestada)? */
    public boolean isInWarZone(MinecraftServer server, ServerWorld world, BlockPos pos, War w) {
        return w.getProvinceId().equals(TerritoryManager.get(server).provinceAt(world, pos));
    }

    /** Guerra activa cuya zona contiene esta posición y en la que participa el jugador, o null. */
    public War warZoneAt(MinecraftServer server, ServerWorld world, BlockPos pos) {
        String province = TerritoryManager.get(server).provinceAt(world, pos);
        if (province == null) {
            return null;
        }
        for (War w : active) {
            if (w.getProvinceId().equals(province)) {
                return w;
            }
        }
        return null;
    }

    // ---- Muerte de un participante ----

    public void onPlayerDeath(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos pos = player.getBlockPos();
        boolean changed = false;
        for (War w : new ArrayList<>(active)) {
            if (!w.isParticipant(player.getUuid())) {
                continue;
            }
            if (isInWarZone(server, world, pos, w)) {
                if (w.eliminated().add(player.getUuid())) {
                    changed = true;
                    announce(server, w, Text.literal(player.getNameForScoreboard()
                            + " ha caído en la guerra y queda eliminado.").formatted(Formatting.GRAY));
                    checkElimination(server, w);
                }
            }
        }
        if (changed) {
            markDirty();
        }
    }

    /** Guerra activa en la que está alistado este aldeano, o null. */
    public War activeWarForVillager(java.util.UUID villager) {
        for (War w : active) {
            if (w.villagerSide(villager) != War.SIDE_NONE) {
                return w;
            }
        }
        return null;
    }

    /** Al morir un aldeano: se le quita de la(s) guerra(s). La revisión de victoria se hace en el tick. */
    public void onVillagerDeath(java.util.UUID villager) {
        boolean changed = false;
        for (War w : new ArrayList<>(active)) {
            changed |= w.removeVillager(villager);
        }
        if (changed) {
            markDirty();
        }
    }

    private int forcesAlive(War w, int side) {
        int players = w.aliveCount(side);
        int villagers = ModConfig.get().villagersCountForVictory ? w.villagerCount(side) : 0;
        return players + villagers;
    }

    private boolean hadForces(War w, int side) {
        if (w.enlistedCount(side) > 0) {
            return true;
        }
        return ModConfig.get().villagersCountForVictory && w.villagerCount(side) > 0;
    }

    /** Si un bando se queda sin fuerzas vivas (habiendo tenido), gana el otro. */
    private void checkElimination(MinecraftServer server, War w) {
        if (w.isEnded()) {
            return;
        }
        boolean atkWiped = hadForces(w, War.SIDE_ATTACKER) && forcesAlive(w, War.SIDE_ATTACKER) == 0;
        boolean defWiped = hadForces(w, War.SIDE_DEFENDER) && forcesAlive(w, War.SIDE_DEFENDER) == 0;
        if (atkWiped && !defWiped) {
            resolve(server, w, w.getDefenderId(), "el atacante fue aniquilado");
        } else if (defWiped && !atkWiped) {
            resolve(server, w, w.getAttackerId(), "el defensor fue aniquilado");
        } else if (atkWiped) { // ambos a cero → sin cambio
            resolve(server, w, "", "ambos bandos cayeron");
        }
    }

    // ---- Rendición / cancelación ----

    public void surrender(MinecraftServer server, War w) {
        resolve(server, w, w.getAttackerId(), "el defensor se rindió");
    }

    public void cancel(MinecraftServer server, War w) {
        resolve(server, w, w.getDefenderId(), "el atacante abandonó");
    }

    // ---- Resolución ----

    /** winnerId = countryId ganador, o "" para empate/sin cambio territorial. */
    public void resolve(MinecraftServer server, War w, String winnerId, String reason) {
        if (w.isEnded()) {
            return;
        }
        w.setEnded(true);
        w.setWinnerId(winnerId);
        GroupManager gm = GroupManager.get(server);
        Group attacker = gm.getById(w.getAttackerId());
        Group defender = gm.getById(w.getDefenderId());

        Text result;
        if (w.getAttackerId().equals(winnerId)) {
            // El atacante conquista la provincia.
            TerritoryManager.get(server).transferProvince(w.getProvinceId(), w.getAttackerId());
            result = Text.literal("Victoria de " + name(attacker) + ": conquista la provincia ("
                    + reason + ").").formatted(Formatting.GOLD);
        } else if (w.getDefenderId().equals(winnerId)) {
            result = Text.literal("Victoria de " + name(defender) + ": conserva su provincia ("
                    + reason + ").").formatted(Formatting.GOLD);
        } else {
            result = Text.literal("La guerra termina sin vencedor (" + reason + ").").formatted(Formatting.GRAY);
        }
        announce(server, w, result);

        active.remove(w);
        history.add(0, w);
        int max = Math.max(0, ModConfig.get().warHistoryMax);
        while (history.size() > max) {
            history.remove(history.size() - 1);
        }
        markDirty();
    }

    private static String name(Group g) {
        return g != null ? g.getName() + " [" + g.getTag() + "]" : "un país";
    }

    // ---- Tick (límite de tiempo) ----

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 1000L) {
            return;
        }
        lastTick = now;
        for (War w : new ArrayList<>(active)) {
            // Revisa si un bando se quedó sin fuerzas (p. ej. al morir aldeanos cuando cuentan).
            checkElimination(server, w);
            if (!w.isEnded() && now >= w.getEnd()) {
                // Se acabó el tiempo: el atacante no logró conquistar → gana el defensor.
                resolve(server, w, w.getDefenderId(), "se acabó el tiempo");
            }
        }
    }

    // ---- Anuncios ----

    public void announce(MinecraftServer server, War w, Text message) {
        RealEconomy.LOGGER.info("[Guerra] {}", message.getString());
        GroupManager gm = GroupManager.get(server);
        Group attacker = gm.getById(w.getAttackerId());
        Group defender = gm.getById(w.getDefenderId());
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            boolean inWar = (attacker != null && attacker.isMember(p.getUuid()))
                    || (defender != null && defender.isMember(p.getUuid()));
            if (inWar) {
                p.sendMessage(message, false);
            }
        }
    }

    // ---- Limpieza ----

    public void removeCountry(MinecraftServer server, String countryId) {
        for (War w : new ArrayList<>(active)) {
            if (w.involves(countryId)) {
                // Si se disuelve un país en guerra, gana automáticamente el otro bando.
                String winner = w.getAttackerId().equals(countryId) ? w.getDefenderId() : w.getAttackerId();
                resolve(server, w, winner, "un país se disolvió");
            }
        }
        history.removeIf(w -> w.involves(countryId));
        markDirty();
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList a = new NbtList();
        for (War w : active) {
            a.add(w.toNbt());
        }
        nbt.put("active", a);
        NbtList h = new NbtList();
        for (War w : history) {
            h.add(w.toNbt());
        }
        nbt.put("history", h);
        return nbt;
    }

    private static WarManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        WarManager mgr = new WarManager();
        NbtList a = nbt.getList("active", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < a.size(); i++) {
            mgr.active.add(War.fromNbt(a.getCompound(i)));
        }
        NbtList h = nbt.getList("history", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < h.size(); i++) {
            mgr.history.add(War.fromNbt(h.getCompound(i)));
        }
        return mgr;
    }
}
