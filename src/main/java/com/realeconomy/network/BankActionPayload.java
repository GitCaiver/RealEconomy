package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: acción del jugador en el banco.
 * action: 0 = ingresar (item→saldo), 1 = retirar (saldo→item), 2 = transferir.
 * target solo se usa en transferir.
 */
public record BankActionPayload(int action, String currencyId, long amount, String target) implements CustomPayload {

    public static final int DEPOSIT = 0;
    public static final int WITHDRAW = 1;
    public static final int TRANSFER = 2;

    public static final CustomPayload.Id<BankActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RealEconomy.MOD_ID, "bank_action"));

    public static final PacketCodec<PacketByteBuf, BankActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.action);
                buf.writeString(value.currencyId);
                buf.writeVarLong(value.amount);
                buf.writeString(value.target == null ? "" : value.target);
            },
            buf -> new BankActionPayload(buf.readVarInt(), buf.readString(), buf.readVarLong(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
