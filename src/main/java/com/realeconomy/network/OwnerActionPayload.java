package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** C2S: acción de dueño sobre un aldeano de MCA. action: 0 = fijar modo (value=id), 1 = desequipar slot (value=0-5). */
public record OwnerActionPayload(UUID villager, int action, int value) implements CustomPayload {

    public static final int SET_MODE = 0;
    public static final int UNEQUIP = 1;

    public static final CustomPayload.Id<OwnerActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "owner_action"));

    public static final PacketCodec<PacketByteBuf, OwnerActionPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeUuid(v.villager);
                buf.writeVarInt(v.action);
                buf.writeVarInt(v.value);
            },
            buf -> new OwnerActionPayload(buf.readUuid(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
