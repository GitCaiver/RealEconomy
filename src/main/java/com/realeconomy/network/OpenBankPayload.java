package com.realeconomy.network;

import com.realeconomy.RealEconomy;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C: abre/actualiza la GUI del banco con los saldos del jugador, la lista de monedas
 * (con su valor actual, para el cambio de moneda) y la comisión de cambio configurada.
 */
public record OpenBankPayload(Map<String, Long> balances, List<BankCurrencyInfo> currencies,
                              int exchangeCommission) implements CustomPayload {

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
        buf.writeVarInt(currencies.size());
        for (BankCurrencyInfo info : currencies) {
            info.write(buf);
        }
        buf.writeVarInt(exchangeCommission);
    }

    private static OpenBankPayload read(PacketByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(buf.readString(), buf.readVarLong());
        }
        int m = buf.readVarInt();
        List<BankCurrencyInfo> list = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            list.add(BankCurrencyInfo.read(buf));
        }
        int commission = buf.readVarInt();
        return new OpenBankPayload(map, list, commission);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
