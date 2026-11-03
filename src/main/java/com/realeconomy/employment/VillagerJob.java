package com.realeconomy.employment;

import java.util.Locale;

/**
 * Trabajos que un aldeano de MCA puede desempeñar al ser empleado. El comportamiento
 * físico de cada uno (romper bloques, talar, etc.) se implementa en la parte 3; el
 * librero (parte 4) y el guardia/guerra (partes 6-7) añaden su lógica propia.
 */
public enum VillagerJob {
    MINERO("minero", "Minero"),
    LENADOR("lenador", "Leñador"),
    AGRICULTOR("agricultor", "Agricultor"),
    GUARDIA("guardia", "Guardia"),
    LIBRERO("librero", "Librero");

    private final String key;
    private final String display;

    VillagerJob(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public int id() {
        return ordinal();
    }

    public static VillagerJob byId(int id) {
        VillagerJob[] v = values();
        return (id < 0 || id >= v.length) ? null : v[id];
    }

    public static VillagerJob parse(String s) {
        if (s == null) {
            return null;
        }
        String n = s.toLowerCase(Locale.ROOT);
        for (VillagerJob j : values()) {
            if (j.key.equals(n) || j.name().toLowerCase(Locale.ROOT).equals(n)) {
                return j;
            }
        }
        return null;
    }
}
