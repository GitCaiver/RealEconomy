package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: abre/actualiza el panel de impuestos por tipo del ayuntamiento. Incluye si el jugador
 * es gobierno (para habilitar la edición) y los valores actuales de cada tipo.
 */
public record OpenTaxPolicyPayload(boolean government, String countryTag, String countryColor,
                                   int rent, int shopSale, int marketSale, int transfer,
                                   int exchange, boolean exchangeDefault,
                                   int auction, boolean auctionDefault,
                                   long shopFee, String shopFeePeriod) implements CustomPayload {

    public static final CustomPayload.Id<OpenTaxPolicyPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_tax_policy"));

    public static final PacketCodec<PacketByteBuf, OpenTaxPolicyPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeBoolean(v.government);
                buf.writeString(v.countryTag);
                buf.writeString(v.countryColor);
                buf.writeVarInt(v.rent);
                buf.writeVarInt(v.shopSale);
                buf.writeVarInt(v.marketSale);
                buf.writeVarInt(v.transfer);
                buf.writeVarInt(v.exchange);
                buf.writeBoolean(v.exchangeDefault);
                buf.writeVarInt(v.auction);
                buf.writeBoolean(v.auctionDefault);
                buf.writeVarLong(v.shopFee);
                buf.writeString(v.shopFeePeriod);
            },
            buf -> new OpenTaxPolicyPayload(buf.readBoolean(), buf.readString(), buf.readString(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarLong(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
