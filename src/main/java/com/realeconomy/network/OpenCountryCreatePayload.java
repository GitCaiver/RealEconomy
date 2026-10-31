package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: abre la interfaz de creación de país (tema Art Déco). El cliente construye
 * las opciones de color y forma política desde las enumeraciones comunes
 * ({@code ColorUtil}, {@code FormType}), así que el payload solo necesita abrir la
 * pantalla; {@code partyless} indica si el jugador puede crear (no está en un país).
 */
public record OpenCountryCreatePayload(boolean canCreate) implements CustomPayload {

    public static final CustomPayload.Id<OpenCountryCreatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_country_create"));

    public static final PacketCodec<PacketByteBuf, OpenCountryCreatePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.canCreate),
            buf -> new OpenCountryCreatePayload(buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
