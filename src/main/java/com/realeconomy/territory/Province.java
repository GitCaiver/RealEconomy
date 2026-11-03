package com.realeconomy.territory;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Una provincia: subdivisión con nombre de un país. Los chunks reclamados pueden
 * asignarse a una provincia (o quedar en el país sin provincia). Las guerras
 * conquistan por provincia; los lugares de trabajo se validan contra el territorio.
 */
public class Province {

    private final String id;
    private String countryId;
    private String name;
    private Formatting color;

    public Province(String id, String countryId, String name, Formatting color) {
        this.id = id;
        this.countryId = countryId;
        this.name = name;
        this.color = color == null ? Formatting.WHITE : color;
    }

    public static Province create(String countryId, String name, Formatting color) {
        return new Province(UUID.randomUUID().toString(), countryId, name, color);
    }

    public String getId() {
        return id;
    }

    public String getCountryId() {
        return countryId;
    }

    public void setCountryId(String countryId) {
        this.countryId = countryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Formatting getColor() {
        return color;
    }

    public void setColor(Formatting color) {
        this.color = color == null ? Formatting.WHITE : color;
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putString("country", countryId);
        c.putString("name", name);
        c.putString("color", color.getName());
        return c;
    }

    public static Province fromNbt(NbtCompound c) {
        Formatting f = Formatting.byName(c.getString("color"));
        return new Province(c.getString("id"), c.getString("country"), c.getString("name"),
                (f == null || !f.isColor()) ? Formatting.WHITE : f);
    }
}
