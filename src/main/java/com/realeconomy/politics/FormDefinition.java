package com.realeconomy.politics;

/**
 * Definición data-driven de una forma política, serializada a JSON en
 * {@code config/realeconomy_forms.json}. El servidor puede ajustar estructura,
 * cómo se llega al poder, quién gobierna y las leyes sin tocar código.
 */
public class FormDefinition {

    /** Nombre de cara al jugador (ayuntamiento, ficha del país, interfaz de creación). */
    public String displayName = "";
    /** Descripción corta para la interfaz de creación. */
    public String description = "";
    /** Cómo se determina el gobierno: nombre de {@link GovernmentMode}. */
    public String governmentMode = GovernmentMode.SINGLE_LEADER.name();
    /** Explicación de cómo se llega al poder (texto libre). */
    public String howToPower = "";
    /** Si esta forma celebra elecciones los domingos. */
    public boolean elections = false;
    /** Si existen partidos en esta forma. */
    public boolean partiesAllowed = true;
    /** Si los partidos tienen poder efectivo (gobiernan) o son decorativos. */
    public boolean partiesHavePower = false;
    /** Leyes de la forma (límites del gobierno). */
    public Laws laws = new Laws();

    public GovernmentMode mode() {
        return GovernmentMode.parse(governmentMode);
    }
}
