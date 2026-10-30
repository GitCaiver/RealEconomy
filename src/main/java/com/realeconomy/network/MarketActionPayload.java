package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: acción del cliente en un mercado. action: 0 = comprar, 1 = vender. */
public record MarketActionPayload(String marketId, int offerIndex, int action, int amount) implements CustomPayload {

    public static final int BUY = 0;
    public static final int SELL = 1;

    public static final CustomPayload.Id<MarketActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "market_action"));

    public static final PacketCodec<PacketByteBuf, MarketActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.marketId);
                buf.writeVarInt(value.offerIndex);
                buf.writeVarInt(value.action);
                buf.writeVarInt(value.amount);
            },
            buf -> new MarketActionPayload(buf.readString(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
