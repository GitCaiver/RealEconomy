package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: abre/actualiza la GUI de un mercado de grupo con sus ofertas. */
public record OpenMarketPayload(String marketId, String label, List<OfferView> offers) implements CustomPayload {

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
        for (OfferView o : offers) {
            buf.writeVarInt(o.index());
            buf.writeVarInt(o.type());
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, o.item());
            buf.writeVarLong(o.price());
            buf.writeString(o.currencyId());
            buf.writeVarInt(o.available());
        }
    }

    private static OpenMarketPayload read(RegistryByteBuf buf) {
        String marketId = buf.readString();
        String label = buf.readString();
        int n = buf.readVarInt();
        List<OfferView> offers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int index = buf.readVarInt();
            int type = buf.readVarInt();
            ItemStack item = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
            long price = buf.readVarLong();
            String currencyId = buf.readString();
            int available = buf.readVarInt();
            offers.add(new OfferView(index, type, item, price, currencyId, available));
        }
        return new OpenMarketPayload(marketId, label, offers);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
