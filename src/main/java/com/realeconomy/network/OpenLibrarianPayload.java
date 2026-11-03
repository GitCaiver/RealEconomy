package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C: abre/actualiza la GUI del librero con los libros a la venta de su ciclo actual. */
public record OpenLibrarianPayload(UUID villager, String title, String currencyName,
                                   long secondsToRotation, List<BookRow> rows) implements CustomPayload {

    public static final CustomPayload.Id<OpenLibrarianPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_librarian"));

    public static final PacketCodec<RegistryByteBuf, OpenLibrarianPayload> CODEC = PacketCodec.of(
            OpenLibrarianPayload::write,
            OpenLibrarianPayload::read
    );

    private void write(RegistryByteBuf buf) {
        buf.writeUuid(villager);
        buf.writeString(title);
        buf.writeString(currencyName);
        buf.writeVarLong(secondsToRotation);
        buf.writeVarInt(rows.size());
        for (BookRow r : rows) {
            buf.writeVarInt(r.index());
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, r.book());
            buf.writeVarLong(r.price());
            buf.writeVarInt(r.units());
        }
    }

    private static OpenLibrarianPayload read(RegistryByteBuf buf) {
        UUID villager = buf.readUuid();
        String title = buf.readString();
        String currencyName = buf.readString();
        long secs = buf.readVarLong();
        int n = buf.readVarInt();
        List<BookRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int index = buf.readVarInt();
            ItemStack book = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
            long price = buf.readVarLong();
            int units = buf.readVarInt();
            rows.add(new BookRow(index, book, price, units));
        }
        return new OpenLibrarianPayload(villager, title, currencyName, secs, rows);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
