package com.realeconomy.network;

import net.minecraft.item.ItemStack;

/**
 * Vista de una oferta de compra del mercado para el cliente (parte 6): el mercado
 * compra {@code item} a precio dinámico. {@code price} es el precio actual por
 * unidad, {@code basePrice} el de referencia y {@code trend} la tendencia.
 */
public record MarketOfferView(int index, ItemStack item, long price, long basePrice,
                              String currencyId, int available, int trend) {

    public static final int STABLE = 0;
    public static final int UP = 1;
    public static final int DOWN = 2;
}
