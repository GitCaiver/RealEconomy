package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** S2C: abre/actualiza la GUI del banco con los saldos del jugador. */
public record OpenBankPayload(Map<String, Long> balances) implements CustomPayload {

    public static final CustomPayload.Id<OpenBankPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "open_bank"));

    public static final PacketCodec<PacketByteBuf, OpenBankPayload> CODEC = PacketCodec.of(
            OpenBankPayload::write,
            OpenBankPayload::read
    );

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(balances.size());
        for (Map.Entry<String, Long> e : balances.entrySet()) {
            buf.writeString(e.getKey());
            buf.writeVarLong(e.getValue());
        }
    }

    private static OpenBankPayload read(PacketByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(buf.readString(), buf.readVarLong());
        }
        return new OpenBankPayload(map);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
