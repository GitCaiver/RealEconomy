package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: edición de un tipo de impuesto desde el panel del ayuntamiento.
 * {@code type} = renta/ventatienda/cuotatienda/mercado/transferencia/cambio/subasta;
 * {@code delta} = incremento a aplicar al valor (p. ej. +1/-1/+5/-5), o cantidad para cuotatienda.
 */
public record TaxPolicyActionPayload(String type, int delta) implements CustomPayload {

    public static final CustomPayload.Id<TaxPolicyActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "tax_policy_action"));

    public static final PacketCodec<PacketByteBuf, TaxPolicyActionPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeString(v.type);
                buf.writeVarInt(v.delta);
            },
            buf -> new TaxPolicyActionPayload(buf.readString(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
