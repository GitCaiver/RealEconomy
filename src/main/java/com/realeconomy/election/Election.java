package com.realeconomy.election;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Una elección activa de un país: ventana de tiempo real, votos por partido y votantes. */
public class Election {

    private final String countryId;
    private final long start;
    private final long end;
    /** partyId -> votos. */
    private final Map<String, Integer> votes = new LinkedHashMap<>();
    /** quién ya ha votado (un voto por miembro). */
    private final Set<UUID> voters = new LinkedHashSet<>();

    public Election(String countryId, long start, long end) {
        this.countryId = countryId;
        this.start = start;
        this.end = end;
    }

    public String getCountryId() {
        return countryId;
    }

    public long getEnd() {
        return end;
    }

    public boolean hasVoted(UUID uuid) {
        return voters.contains(uuid);
    }

    /** Registra un voto. Devuelve false si ese jugador ya había votado. */
    public boolean vote(UUID voter, String partyId) {
        if (voters.contains(voter)) {
            return false;
        }
        voters.add(voter);
        votes.merge(partyId, 1, Integer::sum);
        return true;
    }

    public Map<String, Integer> getVotes() {
        return votes;
    }

    public int totalVotes() {
        int t = 0;
        for (int v : votes.values()) {
            t += v;
        }
        return t;
    }

    /** Partido ganador (más votos); null si no hubo votos. Empates: el primero por orden de voto. */
    public String winner() {
        String best = null;
        int bestVotes = -1;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() > bestVotes) {
                bestVotes = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    // ---- NBT ----

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("country", countryId);
        c.putLong("start", start);
        c.putLong("end", end);
        NbtList voteList = new NbtList();
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            NbtCompound vc = new NbtCompound();
            vc.putString("party", e.getKey());
            vc.putInt("count", e.getValue());
            voteList.add(vc);
        }
        c.put("votes", voteList);
        NbtList voterList = new NbtList();
        for (UUID u : voters) {
            NbtCompound uc = new NbtCompound();
            uc.putUuid("uuid", u);
            voterList.add(uc);
        }
        c.put("voters", voterList);
        return c;
    }

    public static Election fromNbt(NbtCompound c) {
        Election e = new Election(c.getString("country"), c.getLong("start"), c.getLong("end"));
        NbtList voteList = c.getList("votes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < voteList.size(); i++) {
            NbtCompound vc = voteList.getCompound(i);
            e.votes.put(vc.getString("party"), vc.getInt("count"));
        }
        NbtList voterList = c.getList("voters", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < voterList.size(); i++) {
            e.voters.add(voterList.getCompound(i).getUuid("uuid"));
        }
        return e;
    }
}
