package com.realeconomy.util;

/** Parseo y formateo de periodos en tiempo real (s/m/h/d). */
public final class TimeUtil {

    private TimeUtil() {
    }

    /**
     * Convierte un texto como {@code 30s}, {@code 5m}, {@code 2h} o {@code 1d}
     * en milisegundos. Devuelve -1 si el formato no es válido.
     */
    public static long parsePeriod(String s) {
        if (s == null || s.length() < 2) {
            return -1;
        }
        char unit = Character.toLowerCase(s.charAt(s.length() - 1));
        String num = s.substring(0, s.length() - 1);
        long value;
        try {
            value = Long.parseLong(num);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (value <= 0) {
            return -1;
        }
        return switch (unit) {
            case 's' -> value * 1000L;
            case 'm' -> value * 60_000L;
            case 'h' -> value * 3_600_000L;
            case 'd' -> value * 86_400_000L;
            default -> -1;
        };
    }

    /** Formatea una duración en milisegundos de forma legible (p. ej. "1h 30m"). */
    public static String format(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long d = millis / 86_400_000L;
        long h = (millis % 86_400_000L) / 3_600_000L;
        long m = (millis % 3_600_000L) / 60_000L;
        long s = (millis % 60_000L) / 1000L;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("d ");
        }
        if (h > 0) {
            sb.append(h).append("h ");
        }
        if (m > 0) {
            sb.append(m).append("m ");
        }
        if (s > 0 || sb.length() == 0) {
            sb.append(s).append("s");
        }
        return sb.toString().trim();
    }
}
