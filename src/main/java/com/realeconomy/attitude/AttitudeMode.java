package com.realeconomy.attitude;

import java.util.Locale;

/** Modo de actitud de un aldeano de MCA equipado por su dueño. Por defecto PASIVO. */
public enum AttitudeMode {
    PASIVO("pasivo", "Pasivo"),
    NEUTRAL("neutral", "Neutral"),
    AGRESIVO("agresivo", "Agresivo");

    private final String key;
    private final String display;

    AttitudeMode(String key, String display) {
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

    public static AttitudeMode byId(int id) {
        AttitudeMode[] v = values();
        return (id < 0 || id >= v.length) ? PASIVO : v[id];
    }

    public static AttitudeMode parse(String s) {
        if (s == null) {
            return null;
        }
        String n = s.toLowerCase(Locale.ROOT);
        for (AttitudeMode m : values()) {
            if (m.key.equals(n) || m.name().toLowerCase(Locale.ROOT).equals(n)) {
                return m;
            }
        }
        return null;
    }
}
