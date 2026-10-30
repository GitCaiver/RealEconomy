package com.realeconomy.economy;

import net.minecraft.nbt.NbtCompound;

/**
 * Una moneda/divisa. Pertenece a un grupo (en esta versión, una por grupo:
 * id == groupId). El color y las siglas para mostrar se toman del grupo.
 */
public class Currency {

    private final String id;
    private final String groupId;
    private String name;
    private String iconUrl;

    public Currency(String id, String groupId, String name, String iconUrl) {
        this.id = id;
        this.groupId = groupId;
        this.name = name;
        this.iconUrl = iconUrl;
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putString("groupId", groupId);
        c.putString("name", name);
        c.putString("iconUrl", iconUrl == null ? "" : iconUrl);
        return c;
    }

    public static Currency fromNbt(NbtCompound c) {
        String url = c.getString("iconUrl");
        return new Currency(
                c.getString("id"),
                c.getString("groupId"),
                c.getString("name"),
                url.isEmpty() ? null : url
        );
    }
}
