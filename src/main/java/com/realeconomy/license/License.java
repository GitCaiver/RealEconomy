package com.realeconomy.license;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * Definición de una licencia personalizada creada por el gobierno de un país (parte 5).
 * Tiene nombre, precio en una moneda, duración (0 = permanente) y el permiso que otorga.
 */
public class License {

    private final String id;
    private final String countryId;
    private String name;
    private long price;
    private String currencyId;
    /** Duración de validez en milisegundos; 0 = permanente. */
    private long durationMillis;
    private LicensePermission permission;

    public License(String id, String countryId, String name, long price, String currencyId,
                   long durationMillis, LicensePermission permission) {
        this.id = id;
        this.countryId = countryId;
        this.name = name;
        this.price = price;
        this.currencyId = currencyId;
        this.durationMillis = durationMillis;
        this.permission = permission;
    }

    public static License create(String countryId, String name, long price, String currencyId,
                                 long durationMillis, LicensePermission permission) {
        return new License(UUID.randomUUID().toString(), countryId, name, price, currencyId, durationMillis, permission);
    }

    public String getId() {
        return id;
    }

    public String getCountryId() {
        return countryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(String currencyId) {
        this.currencyId = currencyId;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public boolean isPermanent() {
        return durationMillis <= 0;
    }

    public LicensePermission getPermission() {
        return permission;
    }

    public void setPermission(LicensePermission permission) {
        this.permission = permission;
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putString("country", countryId);
        c.putString("name", name);
        c.putLong("price", price);
        c.putString("currency", currencyId == null ? "" : currencyId);
        c.putLong("duration", durationMillis);
        c.putString("perm", permission.name());
        return c;
    }

    public static License fromNbt(NbtCompound c) {
        LicensePermission perm = LicensePermission.TIENDA;
        try {
            perm = LicensePermission.valueOf(c.getString("perm"));
        } catch (IllegalArgumentException ignored) {
            // permiso desconocido (versión antigua): se queda en TIENDA
        }
        return new License(
                c.getString("id"),
                c.getString("country"),
                c.getString("name"),
                c.getLong("price"),
                c.getString("currency"),
                c.getLong("duration"),
                perm);
    }
}
