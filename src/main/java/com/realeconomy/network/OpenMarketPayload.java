package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: abre/actualiza la GUI de un mercado con las ofertas de compra (parte 6). */
public record OpenMarketPayload(String marketId, String label, List<MarketOfferView> offers) implements CustomPayload {

    public static final CustomPayload.Id<OpenMarketPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_market"));

    public static final PacketCodec<RegistryByteBuf, OpenMarketPayload> CODEC = PacketCodec.of(
            OpenMarketPayload::write,
            OpenMarketPayload::read
    );

    private void write(RegistryByteBuf buf) {
        buf.writeString(marketId);
        buf.writeString(label);
        buf.writeVarInt(offers.size());
        for (MarketOfferView o : offers) {
            buf.writeVarInt(o.index());
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, o.item());
            buf.writeVarLong(o.price());
            buf.writeVarLong(o.basePrice());
            buf.writeString(o.currencyId());
            buf.writeVarInt(o.available());
            buf.writeVarInt(o.trend());
        }
    }

    private static OpenMarketPayload read(RegistryByteBuf buf) {
        String marketId = buf.readString();
        String label = buf.readString();
        int n = buf.readVarInt();
        List<MarketOfferView> offers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int index = buf.readVarInt();
            ItemStack item = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
            long price = buf.readVarLong();
            long basePrice = buf.readVarLong();
            String currencyId = buf.readString();
            int available = buf.readVarInt();
            int trend = buf.readVarInt();
            offers.add(new MarketOfferView(index, item, price, basePrice, currencyId, available, trend));
        }
        return new OpenMarketPayload(marketId, label, offers);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
