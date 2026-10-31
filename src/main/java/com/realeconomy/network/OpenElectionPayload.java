package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: abre/actualiza la interfaz de votación de la urna. Incluye el país, si hay
 * elección abierta, cuánto falta para que cierre, si el jugador ya votó, una nota de
 * estado y la lista de partidos a los que puede votar.
 */
public record OpenElectionPayload(String countryTag, String countryColor, boolean open,
                                  long endsInMillis, boolean alreadyVoted, String statusNote,
                                  List<PartyVoteView> parties) implements CustomPayload {

    public static final CustomPayload.Id<OpenElectionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_election"));

    public static final PacketCodec<PacketByteBuf, OpenElectionPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeString(v.countryTag);
                buf.writeString(v.countryColor);
                buf.writeBoolean(v.open);
                buf.writeLong(v.endsInMillis);
                buf.writeBoolean(v.alreadyVoted);
                buf.writeString(v.statusNote);
                buf.writeVarInt(v.parties.size());
                for (PartyVoteView p : v.parties) {
                    PartyVoteView.CODEC.encode(buf, p);
                }
            },
            buf -> {
                String tag = buf.readString();
                String color = buf.readString();
                boolean open = buf.readBoolean();
                long ends = buf.readLong();
                boolean voted = buf.readBoolean();
                String note = buf.readString();
                int n = buf.readVarInt();
                List<PartyVoteView> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(PartyVoteView.CODEC.decode(buf));
                }
                return new OpenElectionPayload(tag, color, open, ends, voted, note, list);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
