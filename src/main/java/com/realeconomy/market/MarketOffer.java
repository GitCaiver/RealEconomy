package com.realeconomy.market;

import com.realeconomy.config.ModConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;

/**
 * Una oferta de un mercado (parte 6, "solo vender"): el mercado <b>compra</b>
 * {@code template} a los jugadores pagando monedas <b>físicas</b> desde su caja.
 * El precio es dinámico por <b>oferta y demanda</b>: parte de {@code basePrice} y
 * un multiplicador que <b>baja al venderse mucho</b> y se <b>recupera con el tiempo
 * real</b> hacia la base. Si {@code marketDynamicPricing} está desactivado, el
 * precio es fijo = base.
 */
public class MarketOffer {

    /** Dirección del precio para mostrar en la interfaz. */
    public enum Trend {STABLE, UP, DOWN}

    private final ItemStack template;
    private final long basePrice;
    private final String currencyId;
    private double multiplier = 1.0;
    private long lastUpdate = System.currentTimeMillis();
    private long lastSale = 0L;

    public MarketOffer(ItemStack template, long basePrice, String currencyId) {
        this.template = template;
        this.basePrice = basePrice;
        this.currencyId = currencyId;
    }

    public ItemStack getTemplate() {
        return template;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    /** Multiplicador actual tras aplicar la recuperación por tiempo real. */
    public double multiplier() {
        if (!ModConfig.get().marketDynamicPricing) {
            return 1.0;
        }
        applyRecovery();
        return multiplier;
    }

    private void applyRecovery() {
        long now = System.currentTimeMillis();
        if (multiplier < 1.0) {
            double secs = (now - lastUpdate) / 1000.0;
            double rec = ModConfig.get().marketRecoveryPerSecond * secs;
            if (rec > 0) {
                multiplier = Math.min(1.0, multiplier + rec);
            }
        }
        lastUpdate = now;
    }

    /** Precio actual por unidad (en monedas), mínimo 1 si la base es positiva. */
    public long currentPrice() {
        if (basePrice <= 0) {
            return 0L;
        }
        long p = Math.round(basePrice * multiplier());
        return Math.max(1L, p);
    }

    /** Registra una venta: baja el multiplicador según las unidades vendidas. */
    public void recordSale(int units) {
        if (units <= 0) {
            return;
        }
        if (ModConfig.get().marketDynamicPricing) {
            applyRecovery();
            double drop = ModConfig.get().marketDropPerUnit * units;
            multiplier = Math.max(ModConfig.get().marketMinMultiplier, multiplier - drop);
            lastUpdate = System.currentTimeMillis();
        }
        lastSale = System.currentTimeMillis();
    }

    /** Tendencia: a la baja si hubo ventas recientes; al alza si se recupera; estable en la base. */
    public Trend trend() {
        if (!ModConfig.get().marketDynamicPricing) {
            return Trend.STABLE;
        }
        double m = multiplier();
        long window = ModConfig.get().marketTrendWindowSeconds * 1000L;
        if (lastSale > 0 && System.currentTimeMillis() - lastSale < window) {
            return Trend.DOWN;
        }
        if (m < 0.999) {
            return Trend.UP;
        }
        return Trend.STABLE;
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound c = new NbtCompound();
        c.put("item", template.encode(lookup));
        c.putLong("base", basePrice);
        c.putString("currency", currencyId);
        c.putDouble("mult", multiplier);
        c.putLong("lastUpdate", lastUpdate);
        c.putLong("lastSale", lastSale);
        return c;
    }

    /**
     * Carga una oferta. Devuelve {@code null} para las ofertas antiguas en las que el
     * mercado <i>vendía</i> al jugador ({@code type == SELL}): ese modo ya no existe
     * (parte 6, "solo vender"), así que se descartan en la migración.
     */
    public static MarketOffer fromNbt(NbtCompound c, RegistryWrapper.WrapperLookup lookup) {
        if (c.contains("type") && "SELL".equals(c.getString("type"))) {
            return null;
        }
        ItemStack item = ItemStack.fromNbt(lookup, c.get("item")).orElse(ItemStack.EMPTY);
        long base = c.contains("base") ? c.getLong("base") : c.getLong("price");
        MarketOffer o = new MarketOffer(item, base, c.getString("currency"));
        if (c.contains("mult")) {
            o.multiplier = c.getDouble("mult");
            o.lastUpdate = c.getLong("lastUpdate");
            o.lastSale = c.getLong("lastSale");
        }
        return o;
    }
}
