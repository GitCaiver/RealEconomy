package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: abre/actualiza la GUI del comprador con el precio por categoría. */
public record OpenBuyerPayload(String label, String currencyName, List<BuyerCategoryView> rows) implements CustomPayload {

    public static final CustomPayload.Id<OpenBuyerPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_buyer"));

    public static final PacketCodec<PacketByteBuf, OpenBuyerPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.label);
                buf.writeString(value.currencyName);
                buf.writeVarInt(value.rows.size());
                for (BuyerCategoryView r : value.rows) {
                    buf.writeString(r.name());
                    buf.writeVarLong(r.coinPrice());
                    buf.writeDouble(r.multiplier());
                }
            },
            buf -> {
                String label = buf.readString();
                String currencyName = buf.readString();
                int n = buf.readVarInt();
                List<BuyerCategoryView> rows = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    rows.add(new BuyerCategoryView(buf.readString(), buf.readVarLong(), buf.readDouble()));
                }
                return new OpenBuyerPayload(label, currencyName, rows);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
