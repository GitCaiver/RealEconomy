package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: el jugador vota por un partido en la urna. */
public record ElectionVotePayload(String partyId) implements CustomPayload {

    public static final CustomPayload.Id<ElectionVotePayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "election_vote"));

    public static final PacketCodec<PacketByteBuf, ElectionVotePayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeString(v.partyId),
            buf -> new ElectionVotePayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
