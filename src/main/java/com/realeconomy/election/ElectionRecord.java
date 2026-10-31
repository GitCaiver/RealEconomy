package com.realeconomy.election;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.LinkedHashMap;
import java.util.Map;

/** Entrada del historial de elecciones de un país (fecha, resultados y ganador). */
public class ElectionRecord {

    public final String countryId;
    public final long time;
    public final String winnerName;
    /** nombre de partido -> votos. */
    public final Map<String, Integer> results;

    public ElectionRecord(String countryId, long time, String winnerName, Map<String, Integer> results) {
        this.countryId = countryId;
        this.time = time;
        this.winnerName = winnerName;
        this.results = results;
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("country", countryId);
        c.putLong("time", time);
        c.putString("winner", winnerName == null ? "" : winnerName);
        NbtList list = new NbtList();
        for (Map.Entry<String, Integer> e : results.entrySet()) {
            NbtCompound rc = new NbtCompound();
            rc.putString("name", e.getKey());
            rc.putInt("count", e.getValue());
            list.add(rc);
        }
        c.put("results", list);
        return c;
    }

    public static ElectionRecord fromNbt(NbtCompound c) {
        Map<String, Integer> results = new LinkedHashMap<>();
        NbtList list = c.getList("results", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound rc = list.getCompound(i);
            results.put(rc.getString("name"), rc.getInt("count"));
        }
        return new ElectionRecord(c.getString("country"), c.getLong("time"), c.getString("winner"), results);
    }
}
