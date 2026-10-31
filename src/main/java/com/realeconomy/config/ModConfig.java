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

    // ---- Tema visual de las interfaces (único, reutilizado por todas las pantallas) ----
    /** Textura del panel/marco de madera (nine-slice 72x72, borde 14). Sustituible por resource pack o config. */
    public String themePanelTexture = "realeconomy:textures/gui/theme/panel.png";
    /** Textura del botón crema/dorado (nine-slice 32x20, borde 6). Sustituible por resource pack o config. */
    public String themeButtonTexture = "realeconomy:textures/gui/theme/button.png";
    /** Textura de las casillas hundidas de los listados (nine-slice 26x26, borde 6). */
    public String themeSlotTexture = "realeconomy:textures/gui/theme/slot.png";
    /** Textura del campo de texto (nine-slice 24x24, borde 6). Sustituible por resource pack o config. */
    public String themeFieldTexture = "realeconomy:textures/gui/theme/field.png";
    /** Textura del carril de barra/slider (nine-slice 24x12, borde 6). Sustituible por resource pack o config. */
    public String themeBarTexture = "realeconomy:textures/gui/theme/bar.png";

    // ---- Banco ----
    /** Distancia máxima (bloques) a la que se puede usar un banco. */
    public double bankRange = 6.0;
    /** Identificador de la textura de fondo de la GUI del banco (sustituible por resource pack). */
    public String bankGuiTexture = "realeconomy:textures/gui/bank.png";
    /** Comisión (%) del cambio de moneda en el banco; va a la cuenta del país de la moneda destino. */
    public int bankExchangeCommissionPercent = 2;

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

    // ---- Gobierno / política monetaria (parte 3, 2ª tanda) ----
    /** Si true, el valor de la moneda vs esmeralda depende del dinero en circulación. */
    public boolean devaluationEnabled = true;
    /** Valor base de una moneda (esmeraldas por moneda) con circulación cero. */
    public double currencyBaseValue = 1.0;
    /** Circulación de referencia para la devaluación: a mayor circulación, menor valor. */
    public long currencyBaseCirculation = 1000;
    /** Cota mínima del valor de una moneda (esmeraldas por moneda). */
    public double currencyMinValue = 0.01;
    /** Cota máxima del valor de una moneda (esmeraldas por moneda). */
    public double currencyMaxValue = 100.0;
    /** Máximo de entradas guardadas en el registro de emisiones (imprimir/quemar). */
    public int moneyLogMax = 50;
    /** Periodo de cobro de la tasa de impuestos general (barra 0-100): 30s/10m/1h/1d. */
    public String countryTaxPeriod = "1h";

    // ---- NPCs compradores por categoría (parte 4, 2ª tanda) ----
    /** Si true, los precios de los compradores se mueven por oferta/demanda; si false, son fijos. */
    public boolean buyersDynamicPricing = true;
    /** Precio base (en esmeraldas) de cualquier item que no tenga precio propio. */
    public double buyerBasePrice = 1.0;
    /** Caída del multiplicador por unidad vendida (oferta/demanda). */
    public double buyerDropPerUnit = 0.002;
    /** Recuperación del multiplicador por segundo real (hacia la base). */
    public double buyerRecoveryPerSecond = 0.001;
    /** Suelo del multiplicador de precio (no baja de aquí). */
    public double buyerMinMultiplier = 0.2;
    /** Distancia máxima (bloques) a la que se puede usar un comprador. */
    public double buyerRange = 6.0;
    /** Textura de fondo de la GUI del comprador (sustituible por resource pack). */
    public String buyerGuiTexture = "realeconomy:textures/gui/shop.png";
    /** Categorías de compra: nombre -> tag de items (Minecraft/convencional). */
    public java.util.Map<String, String> buyerCategories = defaultBuyerCategories();
    /** Precio base (esmeraldas) por categoría; si falta, se usa buyerBasePrice. */
    public java.util.Map<String, Double> buyerCategoryPrices = new java.util.LinkedHashMap<>();
    /** Precio base (esmeraldas) por item concreto (id, p. ej. "minecraft:oak_log"); manda sobre la categoría. */
    public java.util.Map<String, Double> buyerItemPrices = new java.util.LinkedHashMap<>();

    private static java.util.Map<String, String> defaultBuyerCategories() {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        m.put("madera", "minecraft:logs");
        m.put("tablones", "minecraft:planks");
        m.put("minerales", "c:ores");
        m.put("carbon", "minecraft:coals");
        m.put("lana", "minecraft:wool");
        m.put("comida", "c:foods");
        m.put("cultivos", "c:crops");
        return m;
    }

    // ---- Subastas (parte 5, 2ª tanda) ----
    /** Precio de una licencia de subasta (se compra en el ayuntamiento). */
    public int auctionLicensePrice = 100;
    /** Siglas del país cuya moneda se usa para pagar la licencia de subasta (vacío = sin configurar). */
    public String auctionLicenseCurrencyTag = "";
    /** Comisión (%) que se lleva la cuenta del país del vendedor al cerrarse una subasta con puja. */
    public int auctionCommissionPercent = 5;
    /** Duración mínima de una subasta (p. ej. 30s, 5m, 1h). */
    public String auctionMinDuration = "30s";
    /** Distancia máxima (bloques) para interactuar con un pedestal de subasta. */
    public double auctionRange = 4.0;
    /** Bloque que se coloca como pedestal de subasta. */
    public String auctionPedestalBlock = "minecraft:oak_fence";
    /** Textura de fondo de la GUI de subastas (sustituible por resource pack). */
    public String auctionGuiTexture = "realeconomy:textures/gui/shop.png";

    // ---- Mercados de grupo (parte 7) ----
    /** Máximo de mercados que puede tener un grupo. */
    public int maxMarketsPerGroup = 2;
    /** Distancia máxima (bloques) a la que se puede usar/gestionar un mercado. */
    public double marketRange = 6.0;
    /** Textura de fondo de la GUI del mercado (sustituible por resource pack). */
    public String marketGuiTexture = "realeconomy:textures/gui/shop.png";

    // ---- Mercado: oferta y demanda (3ª tanda, parte 6) ----
    /** Si está activo, el precio del mercado baja al vender mucho y se recupera con el tiempo real. */
    public boolean marketDynamicPricing = true;
    /** Precio base por unidad (en monedas) con el que nace una oferta si no se indica otro. */
    public long marketBasePrice = 10;
    /** Cuánto baja el multiplicador de precio por cada unidad vendida. */
    public double marketDropPerUnit = 0.002;
    /** Cuánto se recupera el multiplicador por segundo real hacia la base (1.0). */
    public double marketRecoveryPerSecond = 0.001;
    /** Suelo del multiplicador de precio (0-1). */
    public double marketMinMultiplier = 0.2;
    /** Segundos tras una venta en los que la tendencia se muestra a la baja (▼). */
    public int marketTrendWindowSeconds = 30;

    // ---- Formas políticas (4ª tanda) ----
    /** Plutocracia: cuántos de los miembros más ricos forman el gobierno. */
    public int plutocracyGovernors = 1;

    // ---- Leyes del servidor (4ª tanda, parte 4) ----
    /**
     * Leyes del servidor: límites y prohibiciones globales que están POR ENCIMA de las
     * leyes de cualquier forma política. Si una ley del país contradice a estas, gana el servidor.
     */
    public ServerLaws serverLaws = new ServerLaws();

    public static class ServerLaws {
        /** Impuesto máximo (%) que permite el servidor, pase lo que pase. */
        public int maxTaxPercent = 100;
        /** Si el servidor permite imprimir dinero en absoluto. */
        public boolean allowPrintMoney = true;
        /** Máximo que se puede imprimir de una vez en todo el servidor (-1 = sin límite). */
        public long maxPrintPerAction = -1;
        /** Tope global de circulación de una moneda (-1 = sin límite). */
        public long maxCirculation = -1;
        /** Si el servidor permite crear licencias personalizadas. */
        public boolean allowLicenses = true;
    }

    // ---- Elecciones (4ª tanda, parte 3) ----
    /** Día de la semana en que se celebran las elecciones (MONDAY..SUNDAY). */
    public String electionDayOfWeek = "SUNDAY";
    /** Zona horaria para el día de elecciones (p. ej. "Europe/Madrid"); vacío = la del sistema. */
    public String electionZoneId = "";
    /** Distancia máxima (bloques) a la que se puede votar en una urna. */
    public double urnRange = 6.0;
    /** Máximo de entradas guardadas en el historial de elecciones por país. */
    public int electionHistoryMax = 20;

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
