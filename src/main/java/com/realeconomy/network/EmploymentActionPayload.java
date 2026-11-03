package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * C2S: acción sobre el empleo de un aldeano de MCA. {@code action}: 0 = contratar/actualizar
 * (con trabajo, sueldo y periodo), 1 = despedir.
 */
public record EmploymentActionPayload(UUID villager, int action, int jobId, long wage, String period)
        implements CustomPayload {

    public static final int HIRE = 0;
    public static final int FIRE = 1;

    public static final CustomPayload.Id<EmploymentActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "employment_action"));

    public static final PacketCodec<PacketByteBuf, EmploymentActionPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeUuid(v.villager);
                buf.writeVarInt(v.action);
                buf.writeVarInt(v.jobId);
                buf.writeVarLong(v.wage);
                buf.writeString(v.period);
            },
            buf -> new EmploymentActionPayload(buf.readUuid(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarLong(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
