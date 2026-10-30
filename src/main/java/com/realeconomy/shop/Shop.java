package com.realeconomy.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Una tienda de un jugador: dueño, almacén de stock y ofertas. */
public class Shop {

    private final String id;
    private final UUID owner;
    private String ownerName;
    private final ShopStorage storage = new ShopStorage();
    private final List<Offer> offers = new ArrayList<>();

    public Shop(String id, UUID owner, String ownerName) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public static Shop create(UUID owner, String ownerName) {
        return new Shop(UUID.randomUUID().toString(), owner, ownerName);
    }

    public String getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public ShopStorage getStorage() {
        return storage;
    }

    public List<Offer> getOffers() {
        return offers;
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putUuid("owner", owner);
        c.putString("ownerName", ownerName);

        NbtList offerList = new NbtList();
        for (Offer o : offers) {
            offerList.add(o.toNbt(lookup));
        }
        c.put("offers", offerList);

        NbtList storageList = new NbtList();
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.getStack(i);
            if (!stack.isEmpty()) {
                NbtCompound slot = new NbtCompound();
                slot.putInt("slot", i);
                slot.put("item", stack.encode(lookup));
                storageList.add(slot);
            }
        }
        c.put("storage", storageList);
        return c;
    }

    public static Shop fromNbt(NbtCompound c, RegistryWrapper.WrapperLookup lookup) {
        Shop shop = new Shop(c.getString("id"), c.getUuid("owner"), c.getString("ownerName"));
        NbtList offerList = c.getList("offers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < offerList.size(); i++) {
            shop.offers.add(Offer.fromNbt(offerList.getCompound(i), lookup));
        }
        NbtList storageList = c.getList("storage", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < storageList.size(); i++) {
            NbtCompound slot = storageList.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(lookup, slot.get("item")).orElse(ItemStack.EMPTY);
            shop.storage.setStack(slot.getInt("slot"), stack);
        }
        return shop;
    }
}
