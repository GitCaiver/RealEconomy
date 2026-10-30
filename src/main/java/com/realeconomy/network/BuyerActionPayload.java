package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: acción en el comprador. action: 0 = vender todo lo comprable del inventario. */
public record BuyerActionPayload(int action) implements CustomPayload {

    public static final int SELL_INVENTORY = 0;

    public static final CustomPayload.Id<BuyerActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "buyer_action"));

    public static final PacketCodec<PacketByteBuf, BuyerActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.action),
            buf -> new BuyerActionPayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
