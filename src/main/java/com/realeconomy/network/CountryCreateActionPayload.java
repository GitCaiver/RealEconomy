package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: confirma la creación de un país desde la interfaz de creación.
 * {@code formKey}/{@code subtypeKey} son los nombres de enum de {@code FormType}/{@code FormSubtype}.
 */
public record CountryCreateActionPayload(String tag, String name, String colorName,
                                         String formKey, String subtypeKey) implements CustomPayload {

    public static final CustomPayload.Id<CountryCreateActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "country_create_action"));

    public static final PacketCodec<PacketByteBuf, CountryCreateActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.tag);
                buf.writeString(value.name);
                buf.writeString(value.colorName);
                buf.writeString(value.formKey);
                buf.writeString(value.subtypeKey);
            },
            buf -> new CountryCreateActionPayload(buf.readString(), buf.readString(),
                    buf.readString(), buf.readString(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
