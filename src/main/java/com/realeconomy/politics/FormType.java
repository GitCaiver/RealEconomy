package com.realeconomy.politics;

import java.util.List;
import java.util.Locale;

/**
 * Forma política de un país. Cada forma define quién gobierna y cómo se llega al
 * poder; los detalles (estructura concreta y leyes) viven en el archivo editable
 * {@code config/realeconomy_forms.json} ({@link PoliticalForms}). Aquí solo está
 * la enumeración y un nombre/descripción por defecto para la interfaz de creación.
 */
public enum FormType {
    NINGUNA("Ninguna", "Un líder único gobierna el país. Sin elecciones (como hasta ahora)."),
    DEMOCRACIA("Democracia", "Elecciones cada domingo entre los partidos; gobierna el partido ganador."),
    REPUBLICA("República", "Un presidente elegido entre los partidos, con mandato de varios domingos."),
    MONARQUIA_ABSOLUTA("Monarquía absoluta", "Un rey con poder total que elige a su sucesor. Sin elecciones."),
    MONARQUIA_PARLAMENTARIA("Monarquía parlamentaria", "Un rey (jefe de Estado) y un gobierno elegido cada domingo con un primer ministro."),
    DICTADURA("Dictadura", "Un líder (o una junta) con control sin elecciones. Elige un subtipo."),
    OLIGARQUIA("Oligarquía", "Gobiernan unos pocos. Elige un subtipo."),
    TEOCRACIA("Teocracia", "Un sumo sacerdote gobierna, con diezmo religioso y clérigos designados."),
    TECNOCRACIA("Tecnocracia", "Un consejo de expertos con límites a la impresión y la deuda."),
    ANARQUIA("Anarquía", "No hay gobierno: nadie toca la cuenta del país ni crea impuestos o licencias.");

    private final String displayName;
    private final String description;

    FormType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** true si al elegir esta forma hay que elegir además un subtipo. */
    public boolean requiresSubtype() {
        return this == DICTADURA || this == OLIGARQUIA;
    }

    /** Subtipos válidos de esta forma (vacío si no tiene). */
    public List<FormSubtype> subtypes() {
        return switch (this) {
            case DICTADURA -> List.of(FormSubtype.TOTALITARISMO, FormSubtype.AUTOCRACIA, FormSubtype.JUNTA_MILITAR);
            case OLIGARQUIA -> List.of(FormSubtype.ARISTOCRACIA, FormSubtype.PLUTOCRACIA);
            default -> List.of();
        };
    }

    /** Clave canónica para el archivo de formas (p. ej. {@code democracia}, {@code dictadura_junta_militar}). */
    public String key(FormSubtype subtype) {
        if (requiresSubtype() && subtype != null && subtype != FormSubtype.NONE) {
            return name().toLowerCase(Locale.ROOT) + "_" + subtype.name().toLowerCase(Locale.ROOT);
        }
        return name().toLowerCase(Locale.ROOT);
    }

    /** Busca una forma por nombre (en español o por su nombre de enum), sin distinguir mayúsculas. */
    public static FormType parse(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_')
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
        for (FormType t : values()) {
            String dn = t.displayName.toLowerCase(Locale.ROOT)
                    .replace(' ', '_').replace("á", "a").replace("é", "e").replace("í", "i")
                    .replace("ó", "o").replace("ú", "u");
            if (t.name().toLowerCase(Locale.ROOT).equals(s) || dn.equals(s)) {
                return t;
            }
        }
        return null;
    }
}
