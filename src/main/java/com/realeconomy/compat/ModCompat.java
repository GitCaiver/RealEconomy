package com.realeconomy.compat;

import com.realeconomy.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Detección y conmutadores de los mods externos OPCIONALES con los que RealEconomy
 * es compatible (5ª tanda): <b>MCA Reborn</b> (aldeanos con familia, para trabajos y
 * guerra) y <b>Towns and Towers</b> (solo territorios/guerras). RealEconomy funciona
 * igual sin ellos: toda la integración se protege con estas comprobaciones y, en el
 * caso de MCA, se hace por reflexión en runtime (ver {@link com.realeconomy.compat.mca}).
 */
public final class ModCompat {

    /** Mod id de MCA Reborn. */
    public static final String MCA_ID = "mca";
    /** Mod id de Towns and Towers. */
    public static final String TOWNS_ID = "t_and_t";

    private ModCompat() {
    }

    /** ¿Está MCA Reborn cargado en esta instancia? */
    public static boolean isMcaLoaded() {
        return FabricLoader.getInstance().isModLoaded(MCA_ID);
    }

    /** ¿Está Towns and Towers cargado? */
    public static boolean isTownsLoaded() {
        return FabricLoader.getInstance().isModLoaded(TOWNS_ID);
    }

    /** MCA cargado Y habilitado por config: condición para usar cualquier función de MCA. */
    public static boolean mcaEnabled() {
        return isMcaLoaded() && ModConfig.get().enableMcaIntegration;
    }

    /** Towns and Towers cargado Y habilitado por config. */
    public static boolean townsEnabled() {
        return isTownsLoaded() && ModConfig.get().enableTownsIntegration;
    }
}
