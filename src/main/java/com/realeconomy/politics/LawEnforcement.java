package com.realeconomy.politics;

import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.economy.MonetaryManager;
import com.realeconomy.group.Group;
import net.minecraft.server.MinecraftServer;

/**
 * Hace cumplir las LEYES (parte 4, 4ª tanda). Cada forma política tiene sus leyes
 * ({@link Laws}); por encima están las LEYES DEL SERVIDOR (config {@code serverLaws}):
 * si una ley del país supera el límite del servidor, gana el servidor.
 *
 * <p>Los comandos y botones de gobierno llaman a estos métodos antes de actuar y, si
 * no se puede, muestran el motivo que devuelven ({@code null} = permitido).
 */
public final class LawEnforcement {

    private LawEnforcement() {
    }

    private static Laws formLaws(Group g) {
        return PoliticalForms.get(g.getForm(), g.getSubtype()).laws;
    }

    private static boolean requiresVote(Group g) {
        return formLaws(g).requiresVoteBigDecisions;
    }

    /** ¿Permite la forma política de este país tener ejército / ir a la guerra? */
    public static boolean allowsArmy(Group g) {
        return formLaws(g).allowArmy;
    }

    // ---- Impuestos ----

    /** Impuesto máximo efectivo (%): el menor entre la ley de la forma y la del servidor. */
    public static int maxTax(Group g) {
        int formMax = clampPercent(formLaws(g).maxTaxPercent);
        int serverMax = clampPercent(ModConfig.get().serverLaws.maxTaxPercent);
        return Math.min(formMax, serverMax);
    }

    /** Motivo por el que no se puede fijar ese impuesto, o null si se permite. */
    public static String whyCannotSetTax(Group g, int rate) {
        int max = maxTax(g);
        if (rate > max) {
            return "La ley de tu país (" + PoliticalForms.displayName(g.getForm(), g.getSubtype())
                    + ") limita el impuesto al " + max + "%."
                    + (ModConfig.get().serverLaws.maxTaxPercent < formLaws(g).maxTaxPercent
                    ? " (límite del servidor)" : "");
        }
        return null;
    }

    public static boolean isBigTax(Group g, int rate) {
        return requiresVote(g) && rate > formLaws(g).bigTaxThreshold;
    }

    // ---- Impresión de dinero ----

    /** Motivo por el que no se puede imprimir esa cantidad, o null si se permite (sin contar la votación). */
    public static String whyCannotPrint(MinecraftServer server, Group g, long amount) {
        Laws laws = formLaws(g);
        ModConfig.ServerLaws sl = ModConfig.get().serverLaws;
        if (!sl.allowPrintMoney) {
            return "El servidor no permite imprimir dinero.";
        }
        if (!laws.canPrintMoney) {
            return "La ley de tu país (" + PoliticalForms.displayName(g.getForm(), g.getSubtype())
                    + ") no permite imprimir dinero.";
        }
        long maxAction = minPositive(laws.maxPrintPerAction, sl.maxPrintPerAction);
        if (maxAction >= 0 && amount > maxAction) {
            return "La ley limita la impresión a " + maxAction + " por emisión.";
        }
        long maxCirc = minPositive(laws.maxCirculation, sl.maxCirculation);
        if (maxCirc >= 0) {
            Currency c = CurrencyManager.get(server).getByGroup(g.getId());
            if (c != null) {
                long after = MonetaryManager.get(server).circulation(c.getId()) + amount;
                if (after > maxCirc) {
                    return "La ley limita la circulación a " + maxCirc + " (ahora quedaría en " + after + ").";
                }
            }
        }
        return null;
    }

    public static boolean isBigPrint(Group g, long amount) {
        long thr = formLaws(g).bigPrintThreshold;
        return requiresVote(g) && thr >= 0 && amount > thr;
    }

    // ---- Licencias ----

    /** Motivo por el que el jugador no puede crear/editar licencias, o null si se permite. */
    public static String whyCannotManageLicenses(Group g, java.util.UUID player) {
        if (!ModConfig.get().serverLaws.allowLicenses) {
            return "El servidor no permite crear licencias personalizadas.";
        }
        String who = formLaws(g).whoCreatesLicenses;
        if ("NONE".equalsIgnoreCase(who)) {
            return "La ley de tu país no permite crear licencias.";
        }
        if ("LEADER".equalsIgnoreCase(who) && !g.isLeader(player)) {
            return "En tu país solo el líder puede crear licencias.";
        }
        return null;
    }

    // ---- Utilidades ----

    private static int clampPercent(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /** Menor de dos límites tratando -1 como "sin límite". Devuelve -1 si ambos son ilimitados. */
    private static long minPositive(long a, long b) {
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }
}
