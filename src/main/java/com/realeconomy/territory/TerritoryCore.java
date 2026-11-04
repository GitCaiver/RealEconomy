package com.realeconomy.territory;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Núcleo territorial (baluarte): un bloque físico que ancla una provincia. Se coloca con
 * el item "núcleo" y reclama un área a su alrededor. Es indestructible salvo para el bando
 * atacante durante una guerra activa por su provincia; romperlo da la victoria al atacante.
 *
 * @param dim        id de la dimensión donde está el núcleo.
 * @param posLong    posición del bloque empaquetada ({@link BlockPos#asLong()}).
 * @param countryId  país dueño del núcleo.
 * @param provinceId provincia que ancla este núcleo.
 */
public record TerritoryCore(String dim, long posLong, String countryId, String provinceId) {

    public BlockPos pos() {
        return BlockPos.fromLong(posLong);
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("dim", dim);
        c.putLong("pos", posLong);
        c.putString("country", countryId);
        c.putString("province", provinceId);
        return c;
    }

    public static TerritoryCore fromNbt(NbtCompound c) {
        return new TerritoryCore(c.getString("dim"), c.getLong("pos"),
                c.getString("country"), c.getString("province"));
    }
}
