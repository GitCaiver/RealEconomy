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

/**
 * S2C: abre/actualiza la GUI de dueño de un aldeano de MCA (equipo + actitud).
 * {@code equipment} son 6 slots en orden: cabeza, pecho, piernas, pies, mano, mano secundaria.
 */
public record OpenOwnerPayload(UUID villager, String name, int mode, List<ItemStack> equipment)
        implements CustomPayload {

    public static final CustomPayload.Id<OpenOwnerPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_owner"));

    public static final PacketCodec<RegistryByteBuf, OpenOwnerPayload> CODEC = PacketCodec.of(
            OpenOwnerPayload::write,
            OpenOwnerPayload::read
    );

    private void write(RegistryByteBuf buf) {
        buf.writeUuid(villager);
        buf.writeString(name);
        buf.writeVarInt(mode);
        buf.writeVarInt(equipment.size());
        for (ItemStack s : equipment) {
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, s);
        }
    }

    private static OpenOwnerPayload read(RegistryByteBuf buf) {
        UUID villager = buf.readUuid();
        String name = buf.readString();
        int mode = buf.readVarInt();
        int n = buf.readVarInt();
        List<ItemStack> eq = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            eq.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
        }
        return new OpenOwnerPayload(villager, name, mode, eq);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
