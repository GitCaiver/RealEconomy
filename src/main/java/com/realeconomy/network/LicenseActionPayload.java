package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: el jugador compra una licencia desde la GUI del ayuntamiento (parte 5).
 * {@code defId} = id de la definición de licencia a comprar.
 */
public record LicenseActionPayload(String defId) implements CustomPayload {

    public static final CustomPayload.Id<LicenseActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "license_action"));

    public static final PacketCodec<PacketByteBuf, LicenseActionPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeString(v.defId),
            buf -> new LicenseActionPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
