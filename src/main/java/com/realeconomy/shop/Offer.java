package com.realeconomy.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;

/**
 * Una oferta de una tienda.
 * - VENTA: el dueño vende {@code template} (unidad = 1 item) a {@code price}. El
 *   stock se saca del almacén de la tienda; al llegar a 0, la oferta se retira.
 * - COMPRA: el dueño compra {@code template} a {@code price}, hasta {@code remaining}
 *   unidades. El pago sale de su saldo; si se agota el saldo o {@code remaining},
 *   la oferta se retira.
 */
public class Offer {

    public enum Type {SELL, BUY}

    private final Type type;
    private final ItemStack template;
    private final long price;
    private final String currencyId;
    private int remaining; // solo COMPRA

    public Offer(Type type, ItemStack template, long price, String currencyId, int remaining) {
        this.type = type;
        this.template = template;
        this.price = price;
        this.currencyId = currencyId;
        this.remaining = remaining;
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

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound c = new NbtCompound();
        c.putString("type", type.name());
        c.put("item", template.encode(lookup));
        c.putLong("price", price);
        c.putString("currency", currencyId);
        c.putInt("remaining", remaining);
        return c;
    }

    public static Offer fromNbt(NbtCompound c, RegistryWrapper.WrapperLookup lookup) {
        Type type = Type.valueOf(c.getString("type"));
        ItemStack item = ItemStack.fromNbt(lookup, c.get("item")).orElse(ItemStack.EMPTY);
        return new Offer(type, item, c.getLong("price"), c.getString("currency"), c.getInt("remaining"));
    }
}
