package com.realeconomy.politics;

import com.realeconomy.config.ModConfig;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.group.Group;
import com.realeconomy.party.Party;
import com.realeconomy.party.PartyManager;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Decide quién es "gobierno" (quién puede gestionar la economía del país) según la
 * FORMA POLÍTICA del país (parte 2, 4ª tanda). Centraliza lo que antes resolvía
 * {@code Group.isGovernment}: ahora depende del {@link GovernmentMode} de la forma.
 *
 * <ul>
 *   <li>Anarquía (NONE): nadie gobierna.</li>
 *   <li>Plutocracia: los N miembros más ricos del país (config {@code plutocracyGovernors}).</li>
 *   <li>Democracia / parlamentaria (ELECTED_PARTY / PARLIAMENTARY): el fundador del partido
 *       que gobierna (lo fijan las elecciones); si aún no hay, no gobierna nadie.</li>
 *   <li>República (ELECTED_PRESIDENT): el presidente electo; si no hay, nadie.</li>
 *   <li>Resto (líder único, monarquía, junta, consejo, teocracia): líder + ministros.</li>
 * </ul>
 */
public final class Governance {

    private Governance() {
    }

    public static boolean isGovernment(MinecraftServer server, Group g, UUID player) {
        if (g == null || player == null || !g.isMember(player)) {
            return false;
        }
        GovernmentMode mode = PoliticalForms.get(g.getForm(), g.getSubtype()).mode();
        return switch (mode) {
            case NONE -> false;
            case PLUTOCRACY -> richestGovernors(server, g).contains(player);
            case ELECTED_PARTY, PARLIAMENTARY -> isGoverningPartyHead(server, g, player);
            case ELECTED_PRESIDENT -> player.equals(g.getPresident());
            default -> g.isGovernment(player); // líder + ministros
        };
    }

    /** El fundador del partido que gobierna (democracia/parlamentaria). */
    private static boolean isGoverningPartyHead(MinecraftServer server, Group g, UUID player) {
        String pid = g.getGoverningPartyId();
        if (pid == null) {
            return false;
        }
        Party p = PartyManager.get(server).getById(pid);
        return p != null && p.isLeader(player);
    }

    /** Conjunto de los N miembros más ricos del país (en su moneda). */
    public static List<UUID> richestGovernors(MinecraftServer server, Group g) {
        List<UUID> result = new ArrayList<>();
        Currency c = CurrencyManager.get(server).getByGroup(g.getId());
        if (c == null) {
            return result;
        }
        BalanceManager bm = BalanceManager.get(server);
        List<UUID> members = new ArrayList<>(g.getMembers());
        members.sort(Comparator.comparingLong((UUID u) -> bm.get(u, c.getId())).reversed());
        int n = Math.max(1, ModConfig.get().plutocracyGovernors);
        for (int i = 0; i < Math.min(n, members.size()); i++) {
            // Solo cuentan quienes tienen algo de saldo; si nadie tiene, no gobierna nadie.
            if (bm.get(members.get(i), c.getId()) > 0) {
                result.add(members.get(i));
            }
        }
        return result;
    }

    /** Mensaje claro del motivo por el que el jugador no puede gestionar el país. */
    public static String notGovernmentMessage(MinecraftServer server, Group g) {
        if (g == null) {
            return "No perteneces a ningún país.";
        }
        GovernmentMode mode = PoliticalForms.get(g.getForm(), g.getSubtype()).mode();
        return switch (mode) {
            case NONE -> "Tu país es una anarquía: nadie gobierna, no se puede gestionar la cuenta, "
                    + "impuestos, licencias ni obras públicas.";
            case PLUTOCRACY -> "En una plutocracia solo gobiernan los miembros más ricos del país.";
            case ELECTED_PARTY, PARLIAMENTARY -> g.getGoverningPartyId() == null
                    ? "Aún no hay gobierno electo. Hace falta una elección (el domingo o /ecoadmin elecciones)."
                    : "Solo el gobierno electo (el partido ganador) puede gestionar el país.";
            case ELECTED_PRESIDENT -> g.getPresident() == null
                    ? "Aún no hay presidente. Hace falta una elección (el domingo o /ecoadmin elecciones)."
                    : "Solo el presidente electo puede gestionar el país.";
            case MONARCH -> "Solo el rey (y los ministros que designe) gobierna este país.";
            case THEOCRACY -> "Solo el sumo sacerdote y los clérigos gobiernan esta teocracia.";
            case JUNTA -> "Solo la junta militar (líder y sus miembros) gobierna este país.";
            case COUNCIL -> "Solo el consejo (líder y los designados) gobierna este país.";
            default -> "Solo el gobierno (líder o ministros) puede gestionar el país.";
        };
    }

    /** Etiqueta de los miembros del gobierno según la forma (para mensajes y GUI). */
    public static String councilLabel(Group g) {
        return switch (PoliticalForms.get(g.getForm(), g.getSubtype()).mode()) {
            case JUNTA -> "junta";
            case COUNCIL -> "consejo";
            case THEOCRACY -> "clero";
            default -> "ministros";
        };
    }
}
