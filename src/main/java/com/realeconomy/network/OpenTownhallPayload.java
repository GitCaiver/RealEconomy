package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: abre/actualiza la GUI del ayuntamiento. */
public record OpenTownhallPayload(int price, String currencyId, String currencyName,
                                  int available, boolean configured) implements CustomPayload {

    public static final CustomPayload.Id<OpenTownhallPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_townhall"));

    public static final PacketCodec<PacketByteBuf, OpenTownhallPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.price);
                buf.writeString(value.currencyId);
                buf.writeString(value.currencyName);
                buf.writeVarInt(value.available);
                buf.writeBoolean(value.configured);
            },
            buf -> new OpenTownhallPayload(buf.readVarInt(), buf.readString(), buf.readString(),
                    buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
