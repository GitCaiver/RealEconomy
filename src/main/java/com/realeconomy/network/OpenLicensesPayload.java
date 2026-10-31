package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: abre/actualiza el panel de licencias personalizadas del ayuntamiento (parte 5). Incluye
 * las siglas/color del país, si el jugador es gobierno (para mostrar la ayuda de gestión) y la
 * lista de licencias con el estado de posesión del jugador.
 */
public record OpenLicensesPayload(boolean government, String countryTag, String countryColor,
                                  List<LicenseView> licenses) implements CustomPayload {

    public static final CustomPayload.Id<OpenLicensesPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_licenses"));

    public static final PacketCodec<PacketByteBuf, OpenLicensesPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeBoolean(v.government);
                buf.writeString(v.countryTag);
                buf.writeString(v.countryColor);
                buf.writeVarInt(v.licenses.size());
                for (LicenseView l : v.licenses) {
                    l.write(buf);
                }
            },
            buf -> {
                boolean government = buf.readBoolean();
                String tag = buf.readString();
                String color = buf.readString();
                int n = buf.readVarInt();
                List<LicenseView> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(LicenseView.read(buf));
                }
                return new OpenLicensesPayload(government, tag, color, list);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
