package com.realeconomy.auction;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;

import java.util.UUID;

/** Una subasta activa sobre un pedestal. */
public class Auction {

    private final int id;
    private final long pedestal; // BlockPos.asLong()
    private final UUID seller;
    private final String sellerName;
    private final ItemStack item;
    private final long startPrice;
    private final String currencyId;
    private final long endTime;

    private long currentBid;
    private UUID currentBidder;
    private String bidderName;
    private UUID displayUuid;

    public Auction(int id, long pedestal, UUID seller, String sellerName, ItemStack item,
                   long startPrice, String currencyId, long endTime) {
        this.id = id;
        this.pedestal = pedestal;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.startPrice = startPrice;
        this.currencyId = currencyId;
        this.endTime = endTime;
    }

    public int getId() {
        return id;
    }

    public long getPedestal() {
        return pedestal;
    }

    public UUID getSeller() {
        return seller;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item;
    }

    public long getStartPrice() {
        return startPrice;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public long getEndTime() {
        return endTime;
    }

    public boolean isExpired(long now) {
        return now >= endTime;
    }

    public boolean hasBids() {
        return currentBidder != null && currentBid > 0;
    }

    public long getCurrentBid() {
        return currentBid;
    }

    public UUID getCurrentBidder() {
        return currentBidder;
    }

    public String getBidderName() {
        return bidderName;
    }

    public void setBid(long bid, UUID bidder, String bidderName) {
        this.currentBid = bid;
        this.currentBidder = bidder;
        this.bidderName = bidderName;
    }

    /** Mínimo que debe superar la próxima puja. */
    public long minNextBid() {
        return hasBids() ? currentBid + 1 : startPrice;
    }

    public UUID getDisplayUuid() {
        return displayUuid;
    }

    public void setDisplayUuid(UUID displayUuid) {
        this.displayUuid = displayUuid;
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound c = new NbtCompound();
        c.putInt("id", id);
        c.putLong("pedestal", pedestal);
        c.putUuid("seller", seller);
        c.putString("sellerName", sellerName);
        c.put("item", item.encode(lookup));
        c.putLong("startPrice", startPrice);
        c.putString("currency", currencyId);
        c.putLong("endTime", endTime);
        c.putLong("currentBid", currentBid);
        if (currentBidder != null) {
            c.putUuid("bidder", currentBidder);
            c.putString("bidderName", bidderName == null ? "" : bidderName);
        }
        if (displayUuid != null) {
            c.putUuid("display", displayUuid);
        }
        return c;
    }

    public static Auction fromNbt(NbtCompound c, RegistryWrapper.WrapperLookup lookup) {
        ItemStack item = ItemStack.fromNbt(lookup, c.getCompound("item")).orElse(ItemStack.EMPTY);
        Auction a = new Auction(
                c.getInt("id"),
                c.getLong("pedestal"),
                c.getUuid("seller"),
                c.getString("sellerName"),
                item,
                c.getLong("startPrice"),
                c.getString("currency"),
                c.getLong("endTime")
        );
        a.currentBid = c.getLong("currentBid");
        if (c.contains("bidder")) {
            a.currentBidder = c.getUuid("bidder");
            a.bidderName = c.getString("bidderName");
        }
        if (c.contains("display")) {
            a.displayUuid = c.getUuid("display");
        }
        return a;
    }
}
