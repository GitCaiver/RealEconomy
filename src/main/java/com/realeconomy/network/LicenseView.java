package com.realeconomy.network;

import net.minecraft.network.PacketByteBuf;

/**
 * Info de una licencia personalizada para la GUI del ayuntamiento (parte 5): id de la definición,
 * nombre, precio, moneda (nombre + color), duración (0 = permanente), descripción del permiso, y
 * la caducidad de la que posee el jugador (-1 = no la tiene, 0 = permanente, otro = epoch ms).
 */
public record LicenseView(String id, String name, long price, String currencyName, String colorName,
                          long durationMillis, String permission, long heldExpiry) {

    public void write(PacketByteBuf buf) {
        buf.writeString(id);
        buf.writeString(name);
        buf.writeVarLong(price);
        buf.writeString(currencyName);
        buf.writeString(colorName);
        buf.writeVarLong(durationMillis);
        buf.writeString(permission);
        buf.writeVarLong(heldExpiry);
    }

    public static LicenseView read(PacketByteBuf buf) {
        return new LicenseView(buf.readString(), buf.readString(), buf.readVarLong(), buf.readString(),
                buf.readString(), buf.readVarLong(), buf.readString(), buf.readVarLong());
    }
}
