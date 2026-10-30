package com.realeconomy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.realeconomy.RealEconomy;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración del mod, en {@code config/realeconomy.json}. Se carga al iniciar
 * y se puede recargar. Los valores con nombres de "parte" futura ya se dejan
 * preparados para no romper el formato más adelante.
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE = new ModConfig();

    // ---- Banco ----
    /** Distancia máxima (bloques) a la que se puede usar un banco. */
    public double bankRange = 6.0;
    /** Identificador de la textura de fondo de la GUI del banco (sustituible por resource pack). */
    public String bankGuiTexture = "realeconomy:textures/gui/bank.png";

    // ---- Ayuntamiento / licencias (parte 4) ----
    /** Precio de una licencia de tienda. */
    public int licensePrice = 100;
    /** Siglas del grupo cuya moneda se usa para pagar la licencia (vacío = sin configurar). */
    public String licenseCurrencyTag = "";
    /** Distancia máxima (bloques) a la que se puede usar un ayuntamiento. */
    public double townhallRange = 6.0;
    /** Textura de fondo de la GUI del ayuntamiento (sustituible por resource pack). */
    public String townhallGuiTexture = "realeconomy:textures/gui/townhall.png";

    // ---- Tiendas (parte 5) ----
    public int maxShopsPerPlayer = 3;
    /** Distancia máxima (bloques) a la que se puede usar/gestionar una tienda. */
    public double shopRange = 6.0;
    /** Textura de fondo de la GUI de compra de la tienda (sustituible por resource pack). */
    public String shopGuiTexture = "realeconomy:textures/gui/shop.png";

    // ---- Partidos políticos ----
    /** Precio de crear un partido (se paga en el ayuntamiento del país, va a la cuenta del país). */
    public int partyPrice = 100;
    /**
     * Siglas del país cuya moneda se usa para pagar la creación de un partido.
     * Vacío = se usa la moneda del propio país del jugador (lo habitual).
     */
    public String partyCurrencyTag = "";

    // ---- Mercados de grupo (parte 7) ----
    /** Máximo de mercados que puede tener un grupo. */
    public int maxMarketsPerGroup = 2;
    /** Distancia máxima (bloques) a la que se puede usar/gestionar un mercado. */
    public double marketRange = 6.0;
    /** Textura de fondo de la GUI del mercado (sustituible por resource pack). */
    public String marketGuiTexture = "realeconomy:textures/gui/shop.png";

    public static ModConfig get() {
        return INSTANCE;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("realeconomy.json");
    }

    public static void load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                String json = Files.readString(p);
                ModConfig loaded = GSON.fromJson(json, ModConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            }
            save(); // reescribe para añadir campos nuevos con sus defaults
            RealEconomy.LOGGER.info("Config cargada de {}", p);
        } catch (Exception e) {
            RealEconomy.LOGGER.error("No se pudo cargar la config, usando valores por defecto", e);
            INSTANCE = new ModConfig();
        }
    }

    public static void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            RealEconomy.LOGGER.error("No se pudo guardar la config", e);
        }
    }
}
