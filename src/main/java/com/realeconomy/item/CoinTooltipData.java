package com.realeconomy.item;

import net.minecraft.item.tooltip.TooltipData;

/**
 * Dato de tooltip (común) que lleva el id de la moneda. El cliente lo convierte
 * en un componente visual que pinta el icono descargado (ver cliente).
 */
public record CoinTooltipData(String currencyId) implements TooltipData {
}
