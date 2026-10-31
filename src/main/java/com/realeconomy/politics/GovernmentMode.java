package com.realeconomy.politics;

import java.util.Locale;

/**
 * Cómo se determina quién es "gobierno" (quién puede tocar la economía del país)
 * en una forma política. Lo consume la parte 2 (estructura de gobierno).
 */
public enum GovernmentMode {
    /** Un único líder (y los ministros que designe). Comportamiento clásico. */
    SINGLE_LEADER,
    /** El partido ganador de las elecciones del domingo; su fundador es jefe de gobierno. */
    ELECTED_PARTY,
    /** Un presidente elegido entre candidatos de los partidos, con mandato de varios domingos. */
    ELECTED_PRESIDENT,
    /** Un rey con poder total que elige a su sucesor. */
    MONARCH,
    /** Un rey (jefe de Estado, sin poder económico) + gobierno elegido con primer ministro. */
    PARLIAMENTARY,
    /** Una junta reducida que decide por mayoría. */
    JUNTA,
    /** Un consejo con título, designado por el consejo anterior (aristocracia/tecnocracia). */
    COUNCIL,
    /** Los miembros más ricos del país (plutocracia). */
    PLUTOCRACY,
    /** Un sumo sacerdote y los clérigos que designe. */
    THEOCRACY,
    /** Nadie: no hay gobierno (anarquía). */
    NONE;

    public static GovernmentMode parse(String s) {
        if (s == null) {
            return SINGLE_LEADER;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SINGLE_LEADER;
        }
    }
}
