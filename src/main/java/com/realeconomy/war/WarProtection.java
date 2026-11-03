package com.realeconomy.war;

import com.realeconomy.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Protección (opcional) del terreno dentro de una zona de guerra. Por defecto el terreno
 * y las estructuras (incluidas las de Towns and Towers) se pueden romper con normalidad en
 * una guerra; si {@code warBreakStructures} está en false, nadie puede romper/modificar
 * bloques dentro de una zona de guerra activa.
 *
 * <p>No depende de qué mod generó el terreno: trabaja con coordenadas de chunk/provincia, así
 * que cubre igual el terreno vanilla, el de Towns and Towers o el de cualquier otro mod.
 */
public final class WarProtection {

    private WarProtection() {
    }

    /** true si este bloque NO se puede romper/modificar ahora mismo por estar en una zona de guerra protegida. */
    public static boolean isProtected(MinecraftServer server, ServerWorld world, BlockPos pos) {
        if (server == null || ModConfig.get().warBreakStructures) {
            return false; // romper permitido (comportamiento por defecto)
        }
        return WarManager.get(server).warZoneAt(server, world, pos) != null;
    }
}
