package com.realeconomy.finance;

import net.minecraft.nbt.NbtCompound;

/**
 * Política de impuestos de un país: varios tipos configurables por separado, además del
 * impuesto general de renta (ese se guarda aparte como {@code taxRate} en {@link FinanceManager}).
 *
 * <p>Cada tipo transaccional es un porcentaje 0–100 (0 = desactivado) que se cobra sobre la
 * transacción y va a la cuenta del país. {@code auctionPercent} y {@code exchangePercent} usan
 * <b>-1 = usar el valor por defecto de la config</b> (para no romper el comportamiento previo).
 * La cuota de tienda es una cantidad fija periódica por tienda.
 */
public class TaxPolicy {

    /** % sobre cada venta en una tienda de jugador (lo paga el comprador sobre el precio). */
    public int shopSalePercent = 0;
    /** % sobre lo que un jugador cobra al vender a un NPC de mercado/comprador. */
    public int marketSalePercent = 0;
    /** % sobre cada transferencia bancaria (lo retiene del importe transferido). */
    public int transferPercent = 0;
    /** % de comisión de subasta; -1 = usar config (auctionCommissionPercent). */
    public int auctionPercent = -1;
    /** % de comisión de cambio de moneda; -1 = usar config (bankExchangeCommissionPercent). */
    public int exchangePercent = -1;

    /** Cuota periódica fija por cada tienda que posea un miembro (0 = desactivada). */
    public long shopFeeAmount = 0L;
    /** Periodo de la cuota de tienda (p. ej. "1d"). */
    public String shopFeePeriod = "1d";
    /** Último cobro de la cuota de tienda (ms, reloj real). */
    public long shopFeeLast = 0L;

    public boolean isDefault() {
        return shopSalePercent == 0 && marketSalePercent == 0 && transferPercent == 0
                && auctionPercent == -1 && exchangePercent == -1 && shopFeeAmount == 0L;
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putInt("shopSale", shopSalePercent);
        c.putInt("marketSale", marketSalePercent);
        c.putInt("transfer", transferPercent);
        c.putInt("auction", auctionPercent);
        c.putInt("exchange", exchangePercent);
        c.putLong("shopFee", shopFeeAmount);
        c.putString("shopFeePeriod", shopFeePeriod);
        c.putLong("shopFeeLast", shopFeeLast);
        return c;
    }

    public static TaxPolicy fromNbt(NbtCompound c) {
        TaxPolicy p = new TaxPolicy();
        p.shopSalePercent = c.getInt("shopSale");
        p.marketSalePercent = c.getInt("marketSale");
        p.transferPercent = c.getInt("transfer");
        p.auctionPercent = c.contains("auction") ? c.getInt("auction") : -1;
        p.exchangePercent = c.contains("exchange") ? c.getInt("exchange") : -1;
        p.shopFeeAmount = c.getLong("shopFee");
        if (c.contains("shopFeePeriod")) {
            p.shopFeePeriod = c.getString("shopFeePeriod");
        }
        p.shopFeeLast = c.getLong("shopFeeLast");
        return p;
    }
}
