package com.realeconomy.politics;

import java.util.Locale;

/** Subtipo de una forma política (dictadura u oligarquía). {@link #NONE} = sin subtipo. */
public enum FormSubtype {
    NONE("—", ""),
    TOTALITARISMO("Totalitarismo", "Un líder con control total, sin partidos ni límites propios del país."),
    AUTOCRACIA("Autocracia", "Un líder; los partidos existen pero no tienen poder."),
    JUNTA_MILITAR("Junta militar", "Un grupo reducido decide por mayoría; el cargo se toma o rota."),
    ARISTOCRACIA("Aristocracia", "Gobiernan unos pocos con título, designados por el consejo anterior."),
    PLUTOCRACIA("Plutocracia", "Gobiernan los miembros con más dinero del país; el voto pesa según la riqueza.");

    private final String displayName;
    private final String description;

    FormSubtype(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** Busca un subtipo por nombre (español o enum). Devuelve null si no existe. */
    public static FormSubtype parse(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (FormSubtype t : values()) {
            if (t.name().toLowerCase(Locale.ROOT).equals(s)
                    || t.displayName.toLowerCase(Locale.ROOT).replace(' ', '_').equals(s)) {
                return t;
            }
        }
        return null;
    }
}
