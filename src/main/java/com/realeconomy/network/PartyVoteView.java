package com.realeconomy.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;

/** Una fila de partido en la interfaz de votación (id, nombre, color). */
public record PartyVoteView(String partyId, String name, String colorName) {

    public static final PacketCodec<PacketByteBuf, PartyVoteView> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeString(v.partyId);
                buf.writeString(v.name);
                buf.writeString(v.colorName);
            },
            buf -> new PartyVoteView(buf.readString(), buf.readString(), buf.readString())
    );
}
