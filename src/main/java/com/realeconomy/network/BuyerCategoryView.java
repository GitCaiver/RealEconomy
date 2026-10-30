package com.realeconomy.network;

/** Fila de categoría para la GUI del comprador: nombre, precio en monedas y multiplicador (0-1). */
public record BuyerCategoryView(String name, long coinPrice, double multiplier) {
}
