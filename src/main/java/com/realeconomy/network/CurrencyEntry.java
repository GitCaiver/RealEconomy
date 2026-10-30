package com.realeconomy.network;

/**
 * Metadatos de una moneda que el cliente necesita para mostrarla:
 * id, nombre, color (nombre de Formatting) y siglas del grupo.
 */
public record CurrencyEntry(String id, String name, String colorName, String tag) {
}
