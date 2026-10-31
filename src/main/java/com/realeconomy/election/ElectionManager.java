package com.realeconomy.election;

import com.realeconomy.RealEconomy;
import com.realeconomy.config.ModConfig;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.party.Party;
import com.realeconomy.party.PartyManager;
import com.realeconomy.politics.GovernmentMode;
import com.realeconomy.politics.PoliticalForms;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestiona las elecciones (parte 3, 4ª tanda): abre una el día de elecciones
 * (todo el domingo por defecto, tiempo real y zona configurables) en los países
 * cuya forma lo requiere y que tengan partidos, recoge los votos de las urnas,
 * y al terminar cuenta, aplica el resultado al gobierno y lo anuncia. Guarda el
 * historial. Todo persiste en el mundo.
 */
public class ElectionManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_elections";
    private static final long CHECK_INTERVAL = 1000L;

    public static final PersistentState.Type<ElectionManager> TYPE = new PersistentState.Type<>(
            ElectionManager::new, ElectionManager::fromNbt, null);

    /** countryId -> elección activa. */
    private final Map<String, Election> active = new HashMap<>();
    /** countryId -> fecha (yyyy-mm-dd) de la última elección cerrada (evita reabrir el mismo día). */
    private final Map<String, String> lastElectionDay = new HashMap<>();
    /** historial (más reciente al final). */
    private final List<ElectionRecord> history = new ArrayList<>();

    private long lastCheck = 0L;

    public static ElectionManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    // ---- Consultas ----

    public Election getActive(String countryId) {
        return active.get(countryId);
    }

    public boolean isOpen(String countryId) {
        return active.containsKey(countryId);
    }

    public List<ElectionRecord> historyFor(String countryId) {
        List<ElectionRecord> out = new ArrayList<>();
        for (ElectionRecord r : history) {
            if (r.countryId.equals(countryId)) {
                out.add(r);
            }
        }
        return out;
    }

    public void removeCountry(String countryId) {
        active.remove(countryId);
        lastElectionDay.remove(countryId);
        history.removeIf(r -> r.countryId.equals(countryId));
        markDirty();
    }

    // ---- Zona / día ----

    private static ZoneId zone() {
        String z = ModConfig.get().electionZoneId;
        if (z == null || z.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(z);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private static DayOfWeek electionDay() {
        try {
            return DayOfWeek.valueOf(ModConfig.get().electionDayOfWeek.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return DayOfWeek.SUNDAY;
        }
    }

    private static boolean isElectionDay(long now) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone()).getDayOfWeek() == electionDay();
    }

    private static String dayString(long now) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone()).toLocalDate().toString();
    }

    /** Milisegundos del final del día (23:59:59.999) en la zona configurada. */
    private static long endOfDay(long now) {
        ZonedDateTime z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone());
        LocalDate date = z.toLocalDate();
        ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(zone()).minusNanos(1_000_000);
        return endOfDay.toInstant().toEpochMilli();
    }

    // ---- Tick (apertura/cierre automáticos) ----

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL) {
            return;
        }
        lastCheck = now;

        GroupManager gm = GroupManager.get(server);
        boolean electionDay = isElectionDay(now);
        String today = dayString(now);

        for (Group g : new ArrayList<>(gm.getGroups())) {
            boolean electoral = PoliticalForms.get(g.getForm(), g.getSubtype()).elections;
            Election e = active.get(g.getId());
            if (e != null) {
                // Cierra si terminó el día de elecciones o venció la ventana.
                if (!electionDay || now >= e.getEnd()) {
                    close(server, g);
                }
                continue;
            }
            if (!electoral) {
                continue;
            }
            boolean hasParties = !PartyManager.get(server).partiesOf(g.getId()).isEmpty();
            boolean alreadyToday = today.equals(lastElectionDay.get(g.getId()));
            if (electionDay && hasParties && !alreadyToday) {
                open(server, g, endOfDay(now));
            }
        }
    }

    // ---- Apertura / cierre ----

    public void open(MinecraftServer server, Group g, long end) {
        active.put(g.getId(), new Election(g.getId(), System.currentTimeMillis(), end));
        markDirty();
        announce(server, g, Text.literal("¡Elecciones abiertas en ").append(tag(g))
                .append(Text.literal("! Vota por un partido en una urna (/elecciones).")).formatted(Formatting.GOLD));
    }

    /** Fuerza abrir elecciones en todos los países electorales con partidos (admin). Devuelve cuántas abrió. */
    public int forceOpenAll(MinecraftServer server) {
        int n = 0;
        long end = System.currentTimeMillis() + 24L * 3600_000L;
        for (Group g : new ArrayList<>(GroupManager.get(server).getGroups())) {
            if (!PoliticalForms.get(g.getForm(), g.getSubtype()).elections) {
                continue;
            }
            if (active.containsKey(g.getId())) {
                continue;
            }
            if (PartyManager.get(server).partiesOf(g.getId()).isEmpty()) {
                continue;
            }
            open(server, g, end);
            n++;
        }
        return n;
    }

    /** Fuerza cerrar todas las elecciones activas (admin). Devuelve cuántas cerró. */
    public int forceCloseAll(MinecraftServer server) {
        int n = 0;
        GroupManager gm = GroupManager.get(server);
        for (String countryId : new ArrayList<>(active.keySet())) {
            Group g = gm.getById(countryId);
            if (g != null) {
                close(server, g);
                n++;
            } else {
                active.remove(countryId);
            }
        }
        return n;
    }

    public void close(MinecraftServer server, Group g) {
        Election e = active.remove(g.getId());
        lastElectionDay.put(g.getId(), dayString(System.currentTimeMillis()));
        markDirty();
        if (e == null) {
            return;
        }
        PartyManager pm = PartyManager.get(server);
        GroupManager gm = GroupManager.get(server);
        GovernmentMode mode = PoliticalForms.get(g.getForm(), g.getSubtype()).mode();

        // Resultados por nombre de partido (para el historial y el anuncio).
        Map<String, Integer> named = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> v : e.getVotes().entrySet()) {
            Party p = pm.getById(v.getKey());
            named.put(p == null ? "(partido disuelto)" : p.getName(), v.getValue());
        }

        String winnerId = e.winner();
        Party winner = winnerId == null ? null : pm.getById(winnerId);
        String winnerName;
        if (winner == null || e.totalVotes() == 0) {
            winnerName = "(sin votos: se mantiene el gobierno)";
            announce(server, g, Text.literal("Elecciones cerradas en ").append(tag(g))
                    .append(Text.literal(": sin votos. Se mantiene el gobierno actual.")).formatted(Formatting.YELLOW));
        } else {
            winnerName = winner.getName();
            applyWinner(gm, g, mode, winner);
            announce(server, g, Text.literal("Elecciones cerradas en ").append(tag(g))
                    .append(Text.literal(": gana " + winner.getName() + " con " + e.getVotes().getOrDefault(winnerId, 0)
                            + " de " + e.totalVotes() + " votos.")).formatted(Formatting.GREEN));
            if (mode == GovernmentMode.ELECTED_PRESIDENT) {
                announce(server, g, Text.literal("Nuevo presidente: " + g.getMemberName(winner.getLeader()) + "."));
            } else {
                announce(server, g, Text.literal("Jefe de gobierno: " + g.getMemberName(winner.getLeader()) + "."));
            }
        }

        // Historial.
        history.add(new ElectionRecord(g.getId(), System.currentTimeMillis(), winnerName, named));
        int max = Math.max(1, ModConfig.get().electionHistoryMax);
        // Recorta el historial de este país a 'max'.
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).countryId.equals(g.getId())) {
                count++;
                if (count > max) {
                    history.remove(i);
                }
            }
        }
        markDirty();
    }

    private void applyWinner(GroupManager gm, Group g, GovernmentMode mode, Party winner) {
        if (mode == GovernmentMode.ELECTED_PRESIDENT) {
            gm.setPresident(g, winner.getLeader());
        } else {
            // ELECTED_PARTY y PARLIAMENTARY: gobierna el partido ganador.
            gm.setGoverningParty(g, winner.getId());
        }
    }

    // ---- Voto ----

    public enum VoteResult { OK, NO_ELECTION, ALREADY_VOTED, BAD_PARTY }

    public VoteResult vote(MinecraftServer server, Group g, ServerPlayerEntity voter, String partyId) {
        Election e = active.get(g.getId());
        if (e == null) {
            return VoteResult.NO_ELECTION;
        }
        if (e.hasVoted(voter.getUuid())) {
            return VoteResult.ALREADY_VOTED;
        }
        Party p = PartyManager.get(server).getById(partyId);
        if (p == null || !p.getCountryId().equals(g.getId())) {
            return VoteResult.BAD_PARTY;
        }
        e.vote(voter.getUuid(), partyId);
        markDirty();
        return VoteResult.OK;
    }

    // ---- Utilidades ----

    private static Text tag(Group g) {
        return Text.literal("[" + g.getTag() + "]").formatted(g.getColor() == null ? Formatting.WHITE : g.getColor());
    }

    private static void announce(MinecraftServer server, Group g, Text message) {
        RealEconomy.LOGGER.info("[Elecciones] {}", message.getString());
        for (UUID uuid : g.getMembers()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p != null) {
                p.sendMessage(message, false);
            }
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList activeList = new NbtList();
        for (Election e : active.values()) {
            activeList.add(e.toNbt());
        }
        nbt.put("active", activeList);

        NbtList lastList = new NbtList();
        for (Map.Entry<String, String> en : lastElectionDay.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("country", en.getKey());
            c.putString("day", en.getValue());
            lastList.add(c);
        }
        nbt.put("lastDay", lastList);

        NbtList histList = new NbtList();
        for (ElectionRecord r : history) {
            histList.add(r.toNbt());
        }
        nbt.put("history", histList);
        return nbt;
    }

    private static ElectionManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ElectionManager m = new ElectionManager();
        NbtList activeList = nbt.getList("active", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < activeList.size(); i++) {
            Election e = Election.fromNbt(activeList.getCompound(i));
            m.active.put(e.getCountryId(), e);
        }
        NbtList lastList = nbt.getList("lastDay", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lastList.size(); i++) {
            NbtCompound c = lastList.getCompound(i);
            m.lastElectionDay.put(c.getString("country"), c.getString("day"));
        }
        NbtList histList = nbt.getList("history", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < histList.size(); i++) {
            m.history.add(ElectionRecord.fromNbt(histList.getCompound(i)));
        }
        return m;
    }
}
