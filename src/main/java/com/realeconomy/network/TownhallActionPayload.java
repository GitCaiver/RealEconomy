package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: acción en el ayuntamiento. action: 0 = comprar licencia. */
public record TownhallActionPayload(int action) implements CustomPayload {

    public static final int BUY_LICENSE = 0;

    public static final CustomPayload.Id<TownhallActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "townhall_action"));

    public static final PacketCodec<PacketByteBuf, TownhallActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.action),
            buf -> new TownhallActionPayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
