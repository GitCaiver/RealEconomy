package com.realeconomy.util;

import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce nombres de color (en español e inglés) a {@link Formatting}.
 */
public final class ColorUtil {

    private static final Map<String, Formatting> NAMES = new LinkedHashMap<>();

    static {
        put("negro", Formatting.BLACK);
        put("azul_oscuro", Formatting.DARK_BLUE);
        put("verde_oscuro", Formatting.DARK_GREEN);
        put("cian_oscuro", Formatting.DARK_AQUA);
        put("rojo_oscuro", Formatting.DARK_RED);
        put("morado_oscuro", Formatting.DARK_PURPLE);
        put("purpura_oscuro", Formatting.DARK_PURPLE);
        put("dorado", Formatting.GOLD);
        put("naranja", Formatting.GOLD);
        put("gris", Formatting.GRAY);
        put("gris_oscuro", Formatting.DARK_GRAY);
        put("azul", Formatting.BLUE);
        put("verde", Formatting.GREEN);
        put("cian", Formatting.AQUA);
        put("aqua", Formatting.AQUA);
        put("rojo", Formatting.RED);
        put("rosa", Formatting.LIGHT_PURPLE);
        put("morado_claro", Formatting.LIGHT_PURPLE);
        put("amarillo", Formatting.YELLOW);
        put("blanco", Formatting.WHITE);
    }

    private ColorUtil() {
    }

    private static void put(String name, Formatting color) {
        NAMES.put(name, color);
    }

    /**
     * Devuelve el {@link Formatting} del color indicado, o null si no es un color válido.
     * Acepta nombres en español (mapa anterior) y en inglés (los propios de Formatting).
     */
    public static Formatting parse(String input) {
        if (input == null) {
            return null;
        }
        String key = input.trim().toLowerCase(Locale.ROOT);
        Formatting f = NAMES.get(key);
        if (f != null) {
            return f;
        }
        f = Formatting.byName(key);
        if (f != null && f.isColor()) {
            return f;
        }
        return null;
    }

    /** Nombres sugeridos (en español) para autocompletado. */
    public static List<String> suggestions() {
        return Arrays.asList("rojo", "verde", "azul", "amarillo", "dorado", "naranja",
                "cian", "rosa", "morado_oscuro", "blanco", "gris", "negro",
                "rojo_oscuro", "verde_oscuro", "azul_oscuro");
    }

    public static String displayNames() {
        return suggestions().stream().collect(Collectors.joining(", "));
    }
}
