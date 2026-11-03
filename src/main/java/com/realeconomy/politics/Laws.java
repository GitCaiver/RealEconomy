package com.realeconomy.politics;

/**
 * Leyes de una forma política: qué puede hacer el gobierno y con qué límites.
 * Es un POJO de configuración (se serializa a JSON con Gson en {@link PoliticalForms}).
 * Por encima de estas leyes están siempre las LEYES DEL SERVIDOR ({@code ServerLaws}
 * en la config): si una ley del país supera el límite del servidor, gana el servidor.
 *
 * <p>Se definen aquí todos los campos; la parte 4 (leyes) los hace cumplir en los
 * comandos y botones de gobierno.
 */
public class Laws {

    /** Impuesto máximo (%) que el gobierno puede fijar (renta y tipos). */
    public int maxTaxPercent = 100;
    /** Si el gobierno puede imprimir dinero. */
    public boolean canPrintMoney = true;
    /** Máximo que se puede imprimir de una vez (-1 = sin límite propio). */
    public long maxPrintPerAction = -1;
    /** Tope de circulación de la moneda (-1 = sin límite). La tecnocracia lo usa. */
    public long maxCirculation = -1;

    /** Si las decisiones grandes requieren votación de los líderes de partido. */
    public boolean requiresVoteBigDecisions = false;
    /** Umbral de impuesto (%) por encima del cual hace falta votación. */
    public int bigTaxThreshold = 100;
    /** Umbral de impresión por encima del cual hace falta votación (-1 = nunca). */
    public long bigPrintThreshold = -1;

    /** Quién puede crear licencias personalizadas: {@code GOVERNMENT}, {@code LEADER} o {@code NONE}. */
    public String whoCreatesLicenses = "GOVERNMENT";

    /** Diezmo religioso fijo (%) de la teocracia (0 = ninguno). */
    public int tithePercent = 0;

    /** Duración del mandato en domingos (república/parlamentaria/presidente). */
    public int mandateSundays = 1;

    /** Si esta forma política permite ejércitos / ir a la guerra. Una forma que lo prohíba bloquea /guerra. */
    public boolean allowArmy = true;

    /** Si esta forma celebra elecciones (duplica {@link FormDefinition#elections} por comodidad). */
    public Laws copy() {
        Laws l = new Laws();
        l.maxTaxPercent = maxTaxPercent;
        l.canPrintMoney = canPrintMoney;
        l.maxPrintPerAction = maxPrintPerAction;
        l.maxCirculation = maxCirculation;
        l.requiresVoteBigDecisions = requiresVoteBigDecisions;
        l.bigTaxThreshold = bigTaxThreshold;
        l.bigPrintThreshold = bigPrintThreshold;
        l.whoCreatesLicenses = whoCreatesLicenses;
        l.tithePercent = tithePercent;
        l.mandateSundays = mandateSundays;
        l.allowArmy = allowArmy;
        return l;
    }
}
