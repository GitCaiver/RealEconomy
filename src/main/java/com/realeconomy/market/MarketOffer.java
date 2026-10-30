package com.realeconomy.market;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;

/**
 * Una oferta de un mercado de grupo, pagada en monedas <b>físicas</b> desde la
 * caja (till) del mercado.
 * - VENTA: el mercado vende {@code template} por {@code price} monedas. El cliente
 *   paga monedas (entran a la caja) y recibe el item del stock de la caja.
 * - COMPRA: el mercado compra {@code template} por {@code price} monedas. El cliente
 *   entrega el item (entra a la caja) y recibe monedas de la caja.
 * En ambos casos el límite lo marca el stock de la caja (items o monedas).
 */
public class MarketOffer {

    public enum Type {SELL, BUY}

    private final Type type;
    private final ItemStack template;
    private final long price;
    private final String currencyId;

    public MarketOffer(Type type, ItemStack template, long price, String currencyId) {
        this.type = type;
        this.template = template;
        this.price = price;
        this.currencyId = currencyId;
    }

    public Type getType() {
        return type;
    }

    public ItemStack getTemplate() {
        return template;
    }

    public long getPrice() {
        return price;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound c = new NbtCompound();
        c.putString("type", type.name());
        c.put("item", template.encode(lookup));
        c.putLong("price", price);
        c.putString("currency", currencyId);
        return c;
    }

    public static MarketOffer fromNbt(NbtCompound c, RegistryWrapper.WrapperLookup lookup) {
        Type type = Type.valueOf(c.getString("type"));
        ItemStack item = ItemStack.fromNbt(lookup, c.get("item")).orElse(ItemStack.EMPTY);
        return new MarketOffer(type, item, c.getLong("price"), c.getString("currency"));
    }
}
