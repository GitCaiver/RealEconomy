package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: lista completa de monedas conocidas (metadatos, sin icono). */
public record CurrencyListPayload(List<CurrencyEntry> entries) implements CustomPayload {

    public static final CustomPayload.Id<CurrencyListPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "currency_list"));

    public static final PacketCodec<PacketByteBuf, CurrencyListPayload> CODEC = PacketCodec.of(
            CurrencyListPayload::write,
            CurrencyListPayload::read
    );

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (CurrencyEntry e : entries) {
            buf.writeString(e.id());
            buf.writeString(e.name());
            buf.writeString(e.colorName());
            buf.writeString(e.tag());
        }
    }

    private static CurrencyListPayload read(PacketByteBuf buf) {
        int n = buf.readVarInt();
        List<CurrencyEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new CurrencyEntry(buf.readString(), buf.readString(), buf.readString(), buf.readString()));
        }
        return new CurrencyListPayload(list);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
