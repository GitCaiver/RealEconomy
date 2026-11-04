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

    // ---- Territorios y provincias (5ª tanda, prerrequisito) ----
    /** Máximo de chunks que puede reclamar un país (-1 = sin límite). */
    public int maxClaimsPerCountry = -1;

    // ---- Núcleo territorial / baluarte (5ª tanda, parte C) ----
    /** Activa el item "núcleo territorial" que reclama un área al colocarlo. */
    public boolean territoryCoreEnabled = true;
    /** Radio en bloques que reclama un núcleo a su alrededor (50 = área de 100x100 con el núcleo en el centro). */
    public int territoryCoreRadius = 50;
    /** Bloque que se coloca físicamente como núcleo (indestructible salvo en guerra). */
    public String territoryCoreBlock = "minecraft:bedrock";
    /** Si colocar un núcleo consume el item (salvo en creativo). */
    public boolean territoryCoreConsumeItem = true;
    /** Precio del núcleo territorial en el ayuntamiento (se paga en la moneda del propio país). */
    public int territoryCorePrice = 100;

    // ---- Compatibilidad con mods externos (5ª tanda) ----
    /** Activa la integración con MCA Reborn (empleo/equipo/guerra de sus aldeanos). Solo surte efecto si MCA está instalado. */
    public boolean enableMcaIntegration = true;
    /** Activa la integración con Towns and Towers (que sus pueblos convivan con territorios/guerras). Solo si T&T está instalado. */
    public boolean enableTownsIntegration = true;

    // ---- Empleo de aldeanos de MCA (5ª tanda, parte 2) ----
    /** Periodo de cobro por defecto del sueldo de un aldeano empleado (s/m/h/d). */
    public String villagerWageDefaultPeriod = "1h";
    /** Máximo de aldeanos de MCA que puede emplear un jugador (-1 = sin límite). */
    public int maxEmployeesPerPlayer = -1;
    /** Máximo de aldeanos de MCA empleados por un país (empleo público) (-1 = sin límite). */
    public int maxEmployeesPerCountry = -1;
    /** Radio máximo (bloques) de la zona de trabajo asignable a un aldeano. */
    public int villagerWorkZoneMaxRadius = 32;

    // ---- Comportamiento físico de los trabajos (5ª tanda, parte 3) ----
    /** Solo trabajan si hay un jugador a esta distancia (bloques) y el chunk está cargado (rendimiento). */
    public double villagerWorkActivationRange = 48.0;
    /** Radio (bloques) alrededor del aldeano en el que busca el siguiente bloque a trabajar. */
    public int villagerSearchRadius = 6;
    /** Límite de bloques rotos por minuto por aldeano. */
    public int villagerMaxBreaksPerMinute = 60;
    /** Items que puede cargar un aldeano antes de volver a dejarlos en el cofre. */
    public int villagerCarryCapacity = 128;
    /** El aldeano gasta durabilidad de una herramienta si la tiene (de momento no implementado, reservado). */
    public boolean villagerUsesToolDurability = false;
    /** Bloques extra que el MINERO puede romper (además de cualquier mena, por tag c:ores). */
    public java.util.List<String> minerExtraBlocks = new java.util.ArrayList<>(java.util.List.of(
            "minecraft:stone", "minecraft:deepslate", "minecraft:dirt", "minecraft:gravel", "minecraft:sand"));
    /** El LEÑADOR también rompe las hojas de los árboles. */
    public boolean loggerBreakLeaves = false;
    /** El LEÑADOR replanta un brote tras talar el árbol. */
    public boolean loggerReplantSapling = true;
    /** Producción extra del AGRICULTOR por ciclo aunque no haya cultivos reales (itemId -> cantidad). Vacío = solo cosecha real. */
    public java.util.Map<String, Integer> farmerProduceTable = new java.util.LinkedHashMap<>();
    /** El GUARDIA ataca a los monstruos hostiles que entren en su zona. */
    public boolean guardAttackMonsters = true;

    // ---- Librero (5ª tanda, parte 4) ----
    /** Lista de libros encantados que puede vender un librero (editable). */
    public java.util.List<BookEntry> librarianBooks = defaultLibrarianBooks();
    /** Cuántos libros distintos pone a la venta cada librero por ciclo. */
    public int librarianBooksPerCycle = 5;
    /** Unidades de cada libro por ciclo. */
    public int librarianUnitsPerBook = 3;
    /** Horas (tiempo real) entre rotaciones de stock del librero. */
    public int librarianRotationHours = 24;
    /** Si el dinero de las ventas del librero va a la cuenta del país (true) o del empleador (false). */
    public boolean librarianRevenueToCountry = false;

    // ---- Equipo y actitud de aldeanos de MCA (5ª tanda, parte 6) ----
    /** Si un aldeano de MCA suelta su equipo (armadura/armas) al morir. */
    public boolean villagerDropsEquipmentOnDeath = false;
    /** Fuera de una guerra, un aldeano AGRESIVO ataca también a jugadores/aldeanos (por defecto no, para evitar abusos). */
    public boolean aggressiveAttacksPlayersOutsideWar = false;

    /** Un libro vendible: encantamiento (id), nivel y precio en esmeraldas (se convierte a la moneda del país). */
    public static class BookEntry {
        public String enchant;
        public int level;
        public double price;

        public BookEntry() {
        }

        public BookEntry(String enchant, int level, double price) {
            this.enchant = enchant;
            this.level = level;
            this.price = price;
        }
    }

    private static java.util.List<BookEntry> defaultLibrarianBooks() {
        java.util.List<BookEntry> l = new java.util.ArrayList<>();
        l.add(new BookEntry("minecraft:unbreaking", 3, 12));
        l.add(new BookEntry("minecraft:mending", 1, 30));
        l.add(new BookEntry("minecraft:protection", 4, 16));
        l.add(new BookEntry("minecraft:respiration", 3, 10));
        l.add(new BookEntry("minecraft:aqua_affinity", 1, 8));
        l.add(new BookEntry("minecraft:efficiency", 5, 18));
        l.add(new BookEntry("minecraft:fortune", 3, 28));
        l.add(new BookEntry("minecraft:luck_of_the_sea", 3, 14));
        l.add(new BookEntry("minecraft:thorns", 3, 12));
        l.add(new BookEntry("minecraft:depth_strider", 3, 12));
        l.add(new BookEntry("minecraft:feather_falling", 4, 14));
        l.add(new BookEntry("minecraft:silk_touch", 1, 22));
        l.add(new BookEntry("minecraft:lure", 3, 12));
        l.add(new BookEntry("minecraft:sharpness", 5, 16));
        l.add(new BookEntry("minecraft:smite", 5, 12));
        l.add(new BookEntry("minecraft:looting", 3, 20));
        l.add(new BookEntry("minecraft:sweeping_edge", 3, 14));
        l.add(new BookEntry("minecraft:breach", 4, 18));
        l.add(new BookEntry("minecraft:fire_aspect", 2, 14));
        l.add(new BookEntry("minecraft:knockback", 2, 8));
        l.add(new BookEntry("minecraft:density", 5, 18));
        l.add(new BookEntry("minecraft:power", 5, 14));
        l.add(new BookEntry("minecraft:punch", 2, 10));
        l.add(new BookEntry("minecraft:flame", 1, 12));
        l.add(new BookEntry("minecraft:infinity", 1, 22));
        l.add(new BookEntry("minecraft:quick_charge", 3, 12));
        l.add(new BookEntry("minecraft:piercing", 4, 12));
        l.add(new BookEntry("minecraft:multishot", 1, 12));
        l.add(new BookEntry("minecraft:impaling", 5, 14));
        l.add(new BookEntry("minecraft:loyalty", 3, 14));
        l.add(new BookEntry("minecraft:channeling", 1, 18));
        l.add(new BookEntry("minecraft:riptide", 3, 14));
        return l;
    }

    // ---- Guerras (5ª tanda, prerrequisito) ----
    /** Duración por defecto de una guerra si no se indica (s/m/h/d). */
    public String warDefaultDuration = "1d";
    /** Duración mínima de una guerra. */
    public String warMinDuration = "5m";
    /** Máximo de guerras guardadas en el historial. */
    public int warHistoryMax = 20;
    /**
     * Si las estructuras de Towns and Towers (y cualquier terreno) se pueden romper/modificar
     * dentro de una zona de guerra. Por defecto sí, como el resto del terreno.
     */
    public boolean warBreakStructures = true;
    /** Máximo de aldeanos de MCA que un jugador puede alistar en una guerra (-1 = sin límite). */
    public int maxVillagersPerPlayerInWar = 5;
    /** Máximo de aldeanos de MCA por bando (país) en una guerra (-1 = sin límite). */
    public int maxVillagersPerCountryInWar = 20;
    /** Si los aldeanos cuentan como participantes para ganar la guerra (por defecto no: la victoria es por jugadores). */
    public boolean villagersCountForVictory = false;

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
        /** Si el servidor permite declarar guerras en absoluto. */
        public boolean allowWar = true;
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
