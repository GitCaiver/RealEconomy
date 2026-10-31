package com.realeconomy.politics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.realeconomy.RealEconomy;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro data-driven de las formas políticas, cargado de
 * {@code config/realeconomy_forms.json}. Cada forma (y subtipo) tiene una
 * {@link FormDefinition} con su estructura, cómo se llega al poder, quién gobierna
 * y sus leyes. El servidor edita ese archivo para ajustarlas sin tocar código.
 *
 * <p>Si falta el archivo, o le faltan formas, se completa con los valores por
 * defecto y se reescribe (así nuevas formas se añaden solas al actualizar el mod).
 */
public final class PoliticalForms {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, FormDefinition>>() {}.getType();

    private static Map<String, FormDefinition> forms = defaults();

    private PoliticalForms() {
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("realeconomy_forms.json");
    }

    /** Carga las formas del archivo, completando las que falten con los defaults. */
    public static void load() {
        Map<String, FormDefinition> defs = defaults();
        try {
            Path p = path();
            if (Files.exists(p)) {
                Map<String, FormDefinition> loaded = GSON.fromJson(Files.readString(p), MAP_TYPE);
                if (loaded != null) {
                    // Las entradas del archivo mandan; se añaden las que falten desde los defaults.
                    for (Map.Entry<String, FormDefinition> e : loaded.entrySet()) {
                        if (e.getValue() != null) {
                            defs.put(e.getKey(), e.getValue());
                        }
                    }
                }
            }
            forms = defs;
            save();
            RealEconomy.LOGGER.info("Formas políticas cargadas de {}", p);
        } catch (Exception e) {
            RealEconomy.LOGGER.error("No se pudieron cargar las formas políticas, usando valores por defecto", e);
            forms = defaults();
        }
    }

    public static void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(forms, MAP_TYPE));
        } catch (Exception e) {
            RealEconomy.LOGGER.error("No se pudieron guardar las formas políticas", e);
        }
    }

    /** Definición de una forma (+ subtipo). Nunca devuelve null: cae a NINGUNA si falta. */
    public static FormDefinition get(FormType type, FormSubtype subtype) {
        if (type == null) {
            type = FormType.NINGUNA;
        }
        FormDefinition d = forms.get(type.key(subtype));
        if (d == null) {
            // Si pidieron un subtipo inexistente, prueba la forma base; si no, NINGUNA.
            d = forms.get(type.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (d == null) {
            d = forms.get(FormType.NINGUNA.name().toLowerCase(java.util.Locale.ROOT));
        }
        return d != null ? d : new FormDefinition();
    }

    public static FormDefinition get(String key) {
        return forms.get(key);
    }

    /** Nombre de cara al jugador de una forma (del archivo si existe, si no el del enum). */
    public static String displayName(FormType type, FormSubtype subtype) {
        FormDefinition d = get(type, subtype);
        String base = (d.displayName == null || d.displayName.isBlank())
                ? type.displayName() : d.displayName;
        if (type.requiresSubtype() && subtype != null && subtype != FormSubtype.NONE) {
            return base + " (" + subtype.displayName() + ")";
        }
        return base;
    }

    // ---- Valores por defecto de cada forma ----

    private static Map<String, FormDefinition> defaults() {
        LinkedHashMap<String, FormDefinition> m = new LinkedHashMap<>();

        m.put("ninguna", def(FormType.NINGUNA, FormSubtype.NONE, GovernmentMode.SINGLE_LEADER,
                "El fundador es el líder. No hay elecciones; puede designar ministros.",
                false, true, false, laws(100, true, -1, false, "GOVERNMENT", 0)));

        FormDefinition democracia = def(FormType.DEMOCRACIA, FormSubtype.NONE, GovernmentMode.ELECTED_PARTY,
                "Cada domingo hay elecciones entre los partidos del país. Gobierna el partido más votado; su fundador es el jefe de gobierno.",
                true, true, true, laws(100, true, -1, false, "GOVERNMENT", 0));
        democracia.laws.mandateSundays = 1;
        m.put("democracia", democracia);

        FormDefinition republica = def(FormType.REPUBLICA, FormSubtype.NONE, GovernmentMode.ELECTED_PRESIDENT,
                "Los miembros eligen presidente entre candidatos de los partidos, con mandato de varios domingos. Los cambios grandes (imprimir mucho, subir mucho los impuestos) necesitan votación de los líderes de partido.",
                true, true, true, laws(80, true, -1, true, "GOVERNMENT", 0));
        republica.laws.mandateSundays = 4;
        republica.laws.bigTaxThreshold = 50;
        republica.laws.bigPrintThreshold = 10000;
        m.put("republica", republica);

        m.put("monarquia_absoluta", def(FormType.MONARQUIA_ABSOLUTA, FormSubtype.NONE, GovernmentMode.MONARCH,
                "Un rey con poder total. Elige a su sucesor con /monarquia sucesor. Sin elecciones.",
                false, true, false, laws(100, true, -1, false, "LEADER", 0)));

        FormDefinition parlamentaria = def(FormType.MONARQUIA_PARLAMENTARIA, FormSubtype.NONE, GovernmentMode.PARLIAMENTARY,
                "El rey es el jefe de Estado y no toca la economía. Cada domingo se elige al gobierno entre los partidos; el fundador del partido ganador es el primer ministro, que gestiona el país.",
                true, true, true, laws(100, true, -1, false, "GOVERNMENT", 0));
        parlamentaria.laws.mandateSundays = 1;
        m.put("monarquia_parlamentaria", parlamentaria);

        m.put("dictadura_totalitarismo", def(FormType.DICTADURA, FormSubtype.TOTALITARISMO, GovernmentMode.SINGLE_LEADER,
                "Un líder con control total, sin partidos ni límites propios del país (solo rigen los del servidor).",
                false, false, false, laws(100, true, -1, false, "LEADER", 0)));

        m.put("dictadura_autocracia", def(FormType.DICTADURA, FormSubtype.AUTOCRACIA, GovernmentMode.SINGLE_LEADER,
                "Un líder gobierna. Los partidos existen pero no tienen poder.",
                false, true, false, laws(100, true, -1, false, "LEADER", 0)));

        m.put("dictadura_junta_militar", def(FormType.DICTADURA, FormSubtype.JUNTA_MILITAR, GovernmentMode.JUNTA,
                "Una junta reducida decide por mayoría. El mando se puede tomar o rotar entre sus miembros.",
                false, false, false, laws(100, true, -1, false, "GOVERNMENT", 0)));

        m.put("oligarquia_aristocracia", def(FormType.OLIGARQUIA, FormSubtype.ARISTOCRACIA, GovernmentMode.COUNCIL,
                "Gobiernan unos pocos con título, designados por el consejo anterior.",
                false, false, false, laws(100, true, -1, false, "GOVERNMENT", 0)));

        m.put("oligarquia_plutocracia", def(FormType.OLIGARQUIA, FormSubtype.PLUTOCRACIA, GovernmentMode.PLUTOCRACY,
                "Gobiernan los miembros con más dinero del país; en las votaciones el voto pesa según la riqueza.",
                false, true, false, laws(100, true, -1, false, "GOVERNMENT", 0)));

        FormDefinition teocracia = def(FormType.TEOCRACIA, FormSubtype.NONE, GovernmentMode.THEOCRACY,
                "Un sumo sacerdote gobierna y designa clérigos como gobierno. Aplica un diezmo religioso fijo.",
                false, false, false, laws(100, true, -1, false, "GOVERNMENT", 10));
        m.put("teocracia", teocracia);

        FormDefinition tecnocracia = def(FormType.TECNOCRACIA, FormSubtype.NONE, GovernmentMode.COUNCIL,
                "Un consejo de expertos designados gobierna según los indicadores del país. No puede superar los límites de impresión y deuda.",
                false, false, false, laws(60, true, 5000, false, "GOVERNMENT", 0));
        tecnocracia.laws.maxCirculation = 100000;
        m.put("tecnocracia", tecnocracia);

        m.put("anarquia", def(FormType.ANARQUIA, FormSubtype.NONE, GovernmentMode.NONE,
                "No hay gobierno. Nadie puede tocar la cuenta del país ni crear impuestos, licencias u obras. Todo es entre jugadores.",
                false, true, false, laws(0, false, -1, false, "NONE", 0)));

        return m;
    }

    private static FormDefinition def(FormType type, FormSubtype subtype, GovernmentMode mode, String howToPower,
                                      boolean elections, boolean partiesAllowed, boolean partiesHavePower, Laws laws) {
        FormDefinition d = new FormDefinition();
        d.displayName = type.requiresSubtype() && subtype != FormSubtype.NONE
                ? type.displayName() + " (" + subtype.displayName() + ")" : type.displayName();
        d.description = subtype != FormSubtype.NONE ? subtype.description() : type.description();
        d.governmentMode = mode.name();
        d.howToPower = howToPower;
        d.elections = elections;
        d.partiesAllowed = partiesAllowed;
        d.partiesHavePower = partiesHavePower;
        d.laws = laws;
        return d;
    }

    private static Laws laws(int maxTax, boolean canPrint, long maxPrint, boolean vote, String whoLicenses, int tithe) {
        Laws l = new Laws();
        l.maxTaxPercent = maxTax;
        l.canPrintMoney = canPrint;
        l.maxPrintPerAction = maxPrint;
        l.requiresVoteBigDecisions = vote;
        l.whoCreatesLicenses = whoLicenses;
        l.tithePercent = tithe;
        return l;
    }
}
