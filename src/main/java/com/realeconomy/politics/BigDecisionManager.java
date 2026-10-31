package com.realeconomy.politics;

import com.realeconomy.RealEconomy;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.economy.MonetaryManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.party.Party;
import com.realeconomy.party.PartyManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestiona las "decisiones grandes" que, por la ley de la forma política, necesitan
 * la aprobación de los LÍDERES DE PARTIDO (parte 4, 4ª tanda). Una propuesta pendiente
 * por país; se aprueba/rechaza por mayoría de los líderes de partido actuales y, al
 * aprobarse, se aplica sola (fijar impuesto o imprimir). Persiste en el mundo.
 */
public class BigDecisionManager extends PersistentState {

    private static final String STATE_ID = RealEconomy.MOD_ID + "_decisions";

    public static final PersistentState.Type<BigDecisionManager> TYPE = new PersistentState.Type<>(
            BigDecisionManager::new, BigDecisionManager::fromNbt, null);

    private final Map<String, BigDecision> pending = new HashMap<>();

    public static BigDecisionManager get(MinecraftServer server) {
        PersistentStateManager psm = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return psm.getOrCreate(TYPE, STATE_ID);
    }

    public BigDecision pendingFor(String countryId) {
        return pending.get(countryId);
    }

    public void removeCountry(String countryId) {
        if (pending.remove(countryId) != null) {
            markDirty();
        }
    }

    /** Líderes de los partidos del país (quienes pueden votar la decisión). */
    private static List<UUID> partyLeaders(MinecraftServer server, Group g) {
        List<UUID> out = new ArrayList<>();
        for (Party p : PartyManager.get(server).partiesOf(g.getId())) {
            out.add(p.getLeader());
        }
        return out;
    }

    private static int needed(int leaders) {
        return Math.max(1, leaders / 2 + 1);
    }

    /** Crea (o reemplaza) la propuesta pendiente y avisa a los líderes de partido. */
    public void propose(MinecraftServer server, Group g, String kind, long value, ServerPlayerEntity proposer) {
        BigDecision d = new BigDecision(g.getId(), kind, value, proposer.getNameForScoreboard(), System.currentTimeMillis());
        pending.put(g.getId(), d);
        markDirty();
        List<UUID> leaders = partyLeaders(server, g);
        Text msg = Text.literal("Propuesta en ").append(tag(g)).append(Text.literal(": " + d.describe()
                        + ". Líderes de partido, votad con /gobierno aprobar o /gobierno rechazar ("
                        + needed(leaders.size()) + " votos necesarios)."))
                .formatted(Formatting.GOLD);
        for (UUID u : leaders) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p != null) {
                p.sendMessage(msg, false);
            }
        }
        proposer.sendMessage(Text.literal("Tu decisión necesita la aprobación de los líderes de partido. "
                + "Propuesta registrada: " + d.describe() + ".").formatted(Formatting.YELLOW), false);
    }

    public enum Vote { NO_PROPOSAL, NOT_LEADER, ALREADY_VOTED, COUNTED, APPROVED, REJECTED }

    public Vote approve(MinecraftServer server, Group g, ServerPlayerEntity voter) {
        return castVote(server, g, voter, true);
    }

    public Vote reject(MinecraftServer server, Group g, ServerPlayerEntity voter) {
        return castVote(server, g, voter, false);
    }

    private Vote castVote(MinecraftServer server, Group g, ServerPlayerEntity voter, boolean yes) {
        BigDecision d = pending.get(g.getId());
        if (d == null) {
            return Vote.NO_PROPOSAL;
        }
        List<UUID> leaders = partyLeaders(server, g);
        if (!leaders.contains(voter.getUuid())) {
            return Vote.NOT_LEADER;
        }
        if (d.approvals.contains(voter.getUuid()) || d.rejections.contains(voter.getUuid())) {
            return Vote.ALREADY_VOTED;
        }
        if (yes) {
            d.approvals.add(voter.getUuid());
        } else {
            d.rejections.add(voter.getUuid());
        }
        markDirty();

        int need = needed(leaders.size());
        // Solo cuentan votos de quienes siguen siendo líderes de partido.
        long yesCount = d.approvals.stream().filter(leaders::contains).count();
        long noCount = d.rejections.stream().filter(leaders::contains).count();

        if (yesCount >= need) {
            apply(server, g, d);
            pending.remove(g.getId());
            markDirty();
            announce(server, g, Text.literal("Aprobada por los líderes de partido: " + d.describe() + ".")
                    .formatted(Formatting.GREEN));
            return Vote.APPROVED;
        }
        if (noCount >= need) {
            pending.remove(g.getId());
            markDirty();
            announce(server, g, Text.literal("Rechazada por los líderes de partido: " + d.describe() + ".")
                    .formatted(Formatting.RED));
            return Vote.REJECTED;
        }
        return Vote.COUNTED;
    }

    private void apply(MinecraftServer server, Group g, BigDecision d) {
        if ("tax".equals(d.kind)) {
            FinanceManager.get(server).setTaxRate(g.getId(), (int) d.value);
        } else if ("print".equals(d.kind)) {
            Currency c = CurrencyManager.get(server).getByGroup(g.getId());
            if (c != null) {
                BalanceManager.get(server).add(FinanceManager.accountUuid(g), c.getId(), d.value);
                MonetaryManager.get(server).print(c.getId(), d.value, "votación");
            }
        }
    }

    private static Text tag(Group g) {
        return Text.literal("[" + g.getTag() + "]").formatted(g.getColor() == null ? Formatting.WHITE : g.getColor());
    }

    private static void announce(MinecraftServer server, Group g, Text message) {
        for (UUID uuid : g.getMembers()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p != null) {
                p.sendMessage(message, false);
            }
        }
    }

    // ---- NBT ----

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (BigDecision d : pending.values()) {
            list.add(d.toNbt());
        }
        nbt.put("pending", list);
        return nbt;
    }

    private static BigDecisionManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        BigDecisionManager m = new BigDecisionManager();
        NbtList list = nbt.getList("pending", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            BigDecision d = BigDecision.fromNbt(list.getCompound(i));
            m.pending.put(d.countryId, d);
        }
        return m;
    }
}
