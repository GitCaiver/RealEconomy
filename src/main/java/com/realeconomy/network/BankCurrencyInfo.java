package com.realeconomy.network;

import net.minecraft.network.PacketByteBuf;

/**
 * Info de una moneda para la GUI del banco (cambio de moneda): id, nombre, siglas del país,
 * color y <b>valor actual</b> (esmeraldas por moneda, según inflación/devaluación).
 */
public record BankCurrencyInfo(String id, String name, String tag, String colorName, double value,
                               int exchangePercent) {

    public void write(PacketByteBuf buf) {
        buf.writeString(id);
        buf.writeString(name);
        buf.writeString(tag);
        buf.writeString(colorName);
        buf.writeDouble(value);
        buf.writeVarInt(exchangePercent);
    }

    public static BankCurrencyInfo read(PacketByteBuf buf) {
        return new BankCurrencyInfo(buf.readString(), buf.readString(), buf.readString(),
                buf.readString(), buf.readDouble(), buf.readVarInt());
    }
}
