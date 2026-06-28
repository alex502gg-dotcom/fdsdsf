package dev.auction.auctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionListing {

    private final String id;
    private final UUID sellerUUID;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long expiresAt; // Unix millis

    public AuctionListing(String id, UUID sellerUUID, String sellerName,
                          ItemStack item, double price, long durationMillis) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.expiresAt = System.currentTimeMillis() + durationMillis;
    }

    // Constructor for loading from file
    public AuctionListing(String id, UUID sellerUUID, String sellerName,
                          ItemStack item, double price, long expiresAt, boolean raw) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public UUID getSellerUUID() { return sellerUUID; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItem() { return item.clone(); }
    public double getPrice() { return price; }
    public long getExpiresAt() { return expiresAt; }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingMillis() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public String getFormattedTimeLeft() {
        long millis = getRemainingMillis();
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours   = minutes / 60;
        long days    = hours / 24;

        if (days > 0)    return days + "d " + (hours % 24) + "h";
        if (hours > 0)   return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
