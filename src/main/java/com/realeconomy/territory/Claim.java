package com.realeconomy.territory;

/**
 * Posesión de un chunk: el país dueño y, opcionalmente, la provincia a la que
 * pertenece dentro de ese país ({@code provinceId} null = país sin provincia).
 */
public record Claim(String countryId, String provinceId) {
}
