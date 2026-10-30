package com.realeconomy.network;

import net.minecraft.item.ItemStack;

/** Vista de una oferta para el cliente. type: 0 = venta, 1 = compra. */
public record OfferView(int index, int type, ItemStack item, long price, String currencyId, int available) {

    public static final int SELL = 0;
    public static final int BUY = 1;
}
