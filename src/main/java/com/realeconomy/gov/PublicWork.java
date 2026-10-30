package com.realeconomy.gov;

import net.minecraft.nbt.NbtCompound;

/**
 * Una obra pública: un pedido de materiales del país. Los jugadores entregan el
 * item indicado y cobran {@code pricePerUnit} por unidad desde la cuenta del país.
 * Se cierra cuando se alcanza la cantidad pedida.
 */
public class PublicWork {

    private final int id;
    private final String countryId;
    private final String itemId;
    private final int required;
    private int delivered;
    private final long pricePerUnit;
    private final String currencyId;

    public PublicWork(int id, String countryId, String itemId, int required, int delivered,
                      long pricePerUnit, String currencyId) {
        this.id = id;
        this.countryId = countryId;
        this.itemId = itemId;
        this.required = required;
        this.delivered = delivered;
        this.pricePerUnit = pricePerUnit;
        this.currencyId = currencyId;
    }

    public int getId() {
        return id;
    }

    public String getCountryId() {
        return countryId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getRequired() {
        return required;
    }

    public int getDelivered() {
        return delivered;
    }

    public void addDelivered(int n) {
        delivered += n;
    }

    public int getRemaining() {
        return Math.max(0, required - delivered);
    }

    public boolean isComplete() {
        return delivered >= required;
    }

    public long getPricePerUnit() {
        return pricePerUnit;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putInt("id", id);
        c.putString("country", countryId);
        c.putString("item", itemId);
        c.putInt("required", required);
        c.putInt("delivered", delivered);
        c.putLong("price", pricePerUnit);
        c.putString("currency", currencyId);
        return c;
    }

    public static PublicWork fromNbt(NbtCompound c) {
        return new PublicWork(
                c.getInt("id"),
                c.getString("country"),
                c.getString("item"),
                c.getInt("required"),
                c.getInt("delivered"),
                c.getLong("price"),
                c.getString("currency")
        );
    }
}
