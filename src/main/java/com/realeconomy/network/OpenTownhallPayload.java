package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: abre/actualiza la GUI del ayuntamiento (licencias + política monetaria del país del jugador). */
public record OpenTownhallPayload(int price, String currencyId, String currencyName,
                                  int available, boolean configured,
                                  boolean hasCountry, String countryTag, String countryColor,
                                  long circulation, double value, boolean canPrint, int taxRate) implements CustomPayload {

    public static final CustomPayload.Id<OpenTownhallPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_townhall"));

    public static final PacketCodec<PacketByteBuf, OpenTownhallPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.price);
                buf.writeString(value.currencyId);
                buf.writeString(value.currencyName);
                buf.writeVarInt(value.available);
                buf.writeBoolean(value.configured);
                buf.writeBoolean(value.hasCountry);
                buf.writeString(value.countryTag);
                buf.writeString(value.countryColor);
                buf.writeLong(value.circulation);
                buf.writeDouble(value.value);
                buf.writeBoolean(value.canPrint);
                buf.writeVarInt(value.taxRate);
            },
            buf -> new OpenTownhallPayload(buf.readVarInt(), buf.readString(), buf.readString(),
                    buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean(), buf.readString(), buf.readString(),
                    buf.readLong(), buf.readDouble(), buf.readBoolean(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
