package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** C2S: comprar el libro de índice {@code index} al librero {@code villager}. */
public record LibrarianActionPayload(UUID villager, int index) implements CustomPayload {

    public static final CustomPayload.Id<LibrarianActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "librarian_action"));

    public static final PacketCodec<PacketByteBuf, LibrarianActionPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeUuid(v.villager);
                buf.writeVarInt(v.index);
            },
            buf -> new LibrarianActionPayload(buf.readUuid(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
