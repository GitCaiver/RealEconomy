package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: bytes PNG del icono de una moneda. */
public record CurrencyIconPayload(String id, byte[] png) implements CustomPayload {

    public static final CustomPayload.Id<CurrencyIconPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "currency_icon"));

    public static final PacketCodec<PacketByteBuf, CurrencyIconPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.id);
                buf.writeByteArray(value.png);
            },
            buf -> new CurrencyIconPayload(buf.readString(), buf.readByteArray())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
