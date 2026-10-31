package com.realeconomy.election;

import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.network.ElectionVotePayload;
import com.realeconomy.network.OpenElectionPayload;
import com.realeconomy.network.PartyVoteView;
import com.realeconomy.party.Party;
import com.realeconomy.party.PartyManager;
import com.realeconomy.politics.PoliticalForms;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Lógica de servidor de la interfaz de votación (urna electoral). */
public final class ElectionActions {

    private ElectionActions() {
    }

    public static void openFor(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null) {
            player.sendMessage(Text.literal("No perteneces a ningún país; no puedes votar.").formatted(Formatting.RED), false);
            return;
        }
        ElectionManager em = ElectionManager.get(server);
        PartyManager pm = PartyManager.get(server);

        List<PartyVoteView> views = new ArrayList<>();
        for (Party p : pm.partiesOf(g.getId())) {
            views.add(new PartyVoteView(p.getId(), p.getName(),
                    (p.getColor() == null ? Formatting.WHITE : p.getColor()).getName()));
        }

        boolean electoral = PoliticalForms.get(g.getForm(), g.getSubtype()).elections;
        Election e = em.getActive(g.getId());
        boolean open = e != null;
        long endsIn = open ? Math.max(0, e.getEnd() - System.currentTimeMillis()) : 0;
        boolean voted = open && e.hasVoted(player.getUuid());

        String note;
        if (!electoral) {
            note = "Tu país (" + PoliticalForms.displayName(g.getForm(), g.getSubtype()) + ") no celebra elecciones.";
        } else if (views.isEmpty()) {
            note = "No hay partidos en tu país. Funda uno con /partido crear.";
        } else if (!open) {
            note = "No hay elección abierta. Se vota el día de elecciones (o con /ecoadmin elecciones).";
        } else if (voted) {
            note = "Ya has votado en esta elección.";
        } else {
            note = "Elige un partido y pulsa Votar.";
        }

        ServerPlayNetworking.send(player, new OpenElectionPayload(
                g.getTag(),
                (g.getColor() == null ? Formatting.WHITE : g.getColor()).getName(),
                open, endsIn, voted, note, views));
    }

    public static void handleVote(ServerPlayerEntity player, ElectionVotePayload action) {
        MinecraftServer server = player.getServer();
        if (!UrnEntities.isNear(player)) {
            player.sendMessage(Text.literal("Debes estar junto a una urna para votar.").formatted(Formatting.RED), false);
            return;
        }
        Group g = GroupManager.get(server).getByPlayer(player.getUuid());
        if (g == null) {
            player.sendMessage(Text.literal("No perteneces a ningún país.").formatted(Formatting.RED), false);
            return;
        }
        ElectionManager.VoteResult r = ElectionManager.get(server).vote(server, g, player, action.partyId());
        switch (r) {
            case OK -> player.sendMessage(Text.literal("Voto registrado. ¡Gracias por participar!").formatted(Formatting.GREEN), false);
            case NO_ELECTION -> player.sendMessage(Text.literal("No hay ninguna elección abierta en tu país.").formatted(Formatting.RED), false);
            case ALREADY_VOTED -> player.sendMessage(Text.literal("Ya has votado en esta elección.").formatted(Formatting.RED), false);
            case BAD_PARTY -> player.sendMessage(Text.literal("Ese partido no existe en tu país.").formatted(Formatting.RED), false);
        }
        openFor(player);
    }
}
