package com.realeconomy.jobs;

import java.util.Locale;

/** Tipos de trabajo por los que un miembro de un grupo puede cobrar. */
public enum Job {
    MINING("mineria", "Minería (romper minerales)"),
    LOGGING("tala", "Tala (romper troncos)"),
    FARMING("agricultura", "Agricultura (cosechar cultivos maduros)"),
    BANKING("banca", "Banca (operar en el banco)");

    private final String key;
    private final String description;

    Job(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    /** Resuelve un trabajo por su clave en español (o por el nombre del enum). */
    public static Job fromKey(String s) {
        if (s == null) {
            return null;
        }
        String norm = s.toLowerCase(Locale.ROOT);
        for (Job j : values()) {
            if (j.key.equals(norm) || j.name().toLowerCase(Locale.ROOT).equals(norm)) {
                return j;
            }
        }
        return null;
    }

    public static String keysList() {
        StringBuilder sb = new StringBuilder();
        for (Job j : values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(j.key);
        }
        return sb.toString();
    }
}
