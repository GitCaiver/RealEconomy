package com.realeconomy.market;

import com.realeconomy.shop.ShopStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un mercado de grupo: pertenece a un grupo (no a un jugador), tiene una caja
 * (till) con stock de items y monedas, y una lista de ofertas de compra/venta.
 */
public class Market {

    private final String id;
    private final String groupId;
    private String groupName;
    private String groupTag;
    private final ShopStorage storage = new ShopStorage();
    private final List<MarketOffer> offers = new ArrayList<>();

    public Market(String id, String groupId, String groupName, String groupTag) {
        this.id = id;
        this.groupId = groupId;
        this.groupName = groupName;
        this.groupTag = groupTag;
    }

    public static Market create(String groupId, String groupName, String groupTag) {
        return new Market(UUID.randomUUID().toString(), groupId, groupName, groupTag);
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getGroupTag() {
        return groupTag;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setGroupTag(String groupTag) {
        this.groupTag = groupTag;
    }

    public ShopStorage getStorage() {
        return storage;
    }

    public List<MarketOffer> getOffers() {
        return offers;
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putString("groupId", groupId);
        c.putString("groupName", groupName);
        c.putString("groupTag", groupTag);

        NbtList offerList = new NbtList();
        for (MarketOffer o : offers) {
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

    public static Market fromNbt(NbtCompound c, RegistryWrapper.WrapperLookup lookup) {
        Market market = new Market(c.getString("id"), c.getString("groupId"),
                c.getString("groupName"), c.getString("groupTag"));
        NbtList offerList = c.getList("offers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < offerList.size(); i++) {
            MarketOffer o = MarketOffer.fromNbt(offerList.getCompound(i), lookup);
            if (o != null) { // null = oferta antigua de venta del mercado (ya no existe)
                market.offers.add(o);
            }
        }
        NbtList storageList = c.getList("storage", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < storageList.size(); i++) {
            NbtCompound slot = storageList.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(lookup, slot.get("item")).orElse(ItemStack.EMPTY);
            market.storage.setStack(slot.getInt("slot"), stack);
        }
        return market;
    }
}
