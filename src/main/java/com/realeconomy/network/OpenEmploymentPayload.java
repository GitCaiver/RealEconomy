package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * S2C: abre/actualiza la GUI de empleo de un aldeano de MCA. {@code mode}: 0 = no puedes
 * gestionar, 1 = empleo público (gobierno, paga el país), 2 = empleo privado (pagas tú).
 */
public record OpenEmploymentPayload(UUID villager, String villagerName, boolean employed, int jobId,
                                    long wage, String period, long wallet, boolean active, int mode,
                                    String currencyName, boolean hasZone, boolean hasChest) implements CustomPayload {

    public static final int MODE_NONE = 0;
    public static final int MODE_PUBLIC = 1;
    public static final int MODE_PRIVATE = 2;

    public static final CustomPayload.Id<OpenEmploymentPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_employment"));

    public static final PacketCodec<PacketByteBuf, OpenEmploymentPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeUuid(v.villager);
                buf.writeString(v.villagerName);
                buf.writeBoolean(v.employed);
                buf.writeVarInt(v.jobId);
                buf.writeVarLong(v.wage);
                buf.writeString(v.period);
                buf.writeVarLong(v.wallet);
                buf.writeBoolean(v.active);
                buf.writeVarInt(v.mode);
                buf.writeString(v.currencyName);
                buf.writeBoolean(v.hasZone);
                buf.writeBoolean(v.hasChest);
            },
            buf -> new OpenEmploymentPayload(
                    buf.readUuid(), buf.readString(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarLong(), buf.readString(), buf.readVarLong(), buf.readBoolean(),
                    buf.readVarInt(), buf.readString(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
