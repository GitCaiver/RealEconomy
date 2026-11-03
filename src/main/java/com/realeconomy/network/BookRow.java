package com.realeconomy.network;

import net.minecraft.item.ItemStack;

/** Fila de la GUI del librero: el libro encantado (icono real + brillo), su precio en monedas y las unidades. */
public record BookRow(int index, ItemStack book, long price, int units) {
}
