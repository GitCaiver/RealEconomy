package com.realeconomy.compat.mca;

import com.realeconomy.RealEconomy;
import com.realeconomy.compat.ModCompat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fachada de integración con MCA Reborn, hecha por <b>reflexión</b> para que MCA sea una
 * dependencia totalmente opcional y el build no dependa de su artefacto (su jar de Fabric
 * usa ClassTweaker, que este Loom no sabe parsear). Todos los métodos comprueban primero
 * {@link ModCompat#mcaEnabled()} y resuelven clases/métodos de forma perezosa; si algo no
 * se encuentra (otra versión de MCA, API cambiada), registran el fallo una vez y devuelven
 * un valor neutro en vez de romper.
 *
 * <p>Las firmas reales de MCA (rama 1.21.1, paquete {@code net.conczin.mca}) se conocen por
 * lectura de su código fuente. Las llamadas a métodos vanilla heredados por el aldeano de
 * MCA se hacen con tipos vanilla normales (no por reflexión).
 */
public final class Mca {

    /** Ids de las entidades "aldeano" vivas de MCA (no cuentan los zombie villager). */
    private static final Set<String> VILLAGER_PATHS = Set.of("male_villager", "female_villager");

    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Mca() {
    }

    // ---- Detección ----

    /**
     * ¿Es esta entidad un aldeano (vivo) de MCA Reborn? Se detecta por el id de su tipo
     * ({@code mca:male_villager} / {@code mca:female_villager}), sin tocar clases de MCA,
     * así que es seguro aunque MCA no esté.
     */
    public static boolean isMcaVillager(Entity entity) {
        if (entity == null || !ModCompat.mcaEnabled()) {
            return false;
        }
        EntityType<?> type = entity.getType();
        Identifier id = Registries.ENTITY_TYPE.getId(type);
        return id != null && ModCompat.MCA_ID.equals(id.getNamespace()) && VILLAGER_PATHS.contains(id.getPath());
    }

    // ---- Familia / dueño ----

    /**
     * ¿Es {@code player} el dueño (familia jugadora) de este aldeano de MCA? El dueño es su
     * pareja jugadora o su padre/madre jugador. Se resuelve por reflexión sobre el sistema de
     * relaciones de MCA (getRelationships → isMarriedTo / getFamilyEntry.father()/mother()).
     */
    public static boolean isOwner(Entity villager, UUID player) {
        if (player == null || !isMcaVillager(villager)) {
            return false;
        }
        Object rel = invoke(method(villager.getClass(), "getRelationships"), villager);
        if (rel == null) {
            return false;
        }
        Object married = invoke(method(rel.getClass(), "isMarriedTo", UUID.class), rel, player);
        if (married instanceof Boolean b && b) {
            return true;
        }
        Object node = invoke(method(rel.getClass(), "getFamilyEntry"), rel);
        if (node != null) {
            Object father = invoke(method(node.getClass(), "father"), node);
            Object mother = invoke(method(node.getClass(), "mother"), node);
            return player.equals(father) || player.equals(mother);
        }
        return false;
    }

    // ---- Núcleo de reflexión (uso interno de este paquete) ----

    static Class<?> cls(String name) {
        try {
            return Class.forName(name, false, Mca.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            warnOnce("class:" + name, "Clase de MCA no encontrada: " + name);
            return null;
        }
    }

    /**
     * Resuelve y cachea un método. Usa primero {@code getMethod} (cubre métodos públicos
     * heredados, incluidos los {@code default} de interfaces, p. ej. isMarriedTo) y, si no,
     * recorre clases declaradas. null si no existe.
     */
    static Method method(Class<?> owner, String name, Class<?>... params) {
        if (owner == null) {
            return null;
        }
        String key = owner.getName() + "#" + name + "/" + params.length;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Method m = owner.getMethod(name, params);
            m.setAccessible(true);
            METHOD_CACHE.put(key, m);
            return m;
        } catch (NoSuchMethodException ignored) {
            // Prueba métodos declarados (no públicos) recorriendo superclases.
        }
        Class<?> c = owner;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                METHOD_CACHE.put(key, m);
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        warnOnce("method:" + key, "Método de MCA no encontrado: " + owner.getName() + "#" + name);
        return null;
    }

    static Object invoke(Method m, Object target, Object... args) {
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            warnOnce("invoke:" + m.getName(), "Error llamando a MCA#" + m.getName() + ": " + t);
            return null;
        }
    }

    static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            RealEconomy.LOGGER.warn("[compat/MCA] {}", message);
        }
    }
}
