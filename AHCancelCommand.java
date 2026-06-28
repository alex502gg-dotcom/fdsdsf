package dev.auction.auctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionListing {

    private final String id;
    private final UUID sellerUUID;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private long expiresAt; // Unix millis (-1 = auto-renew infinite)

    // Premium features
    private boolean autoRenew;       // платное автопродление
    private boolean topSlot;         // топ-позиция в GUI
    private int topSlotDays;         // сколько дней топ оплачено
    private long topSlotExpiresAt;   // когда истекает топ-позиция

    // ── Конструктор при выставлении лота ─────────────────────────────────
    public AuctionListing(String id, UUID sellerUUID, String sellerName,
                          ItemStack item, double price, long durationMillis,
                          boolean autoRenew, boolean topSlot, int topSlotDays) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.autoRenew = autoRenew;
        this.topSlot = topSlot;
        this.topSlotDays = topSlotDays;

        if (autoRenew) {
            // infinite — никогда не истекает само по себе; продлевается при checkExpired
            this.expiresAt = System.currentTimeMillis() + durationMillis;
        } else {
            this.expiresAt = System.currentTimeMillis() + durationMillis;
        }

        this.topSlotExpiresAt = topSlot
                ? System.currentTimeMillis() + (long) topSlotDays * 86_400_000L
                : 0L;
    }

    // ── Конструктор при загрузке из файла ────────────────────────────────
    public AuctionListing(String id, UUID sellerUUID, String sellerName,
                          ItemStack item, double price, long expiresAt, boolean raw) {
        this(id, sellerUUID, sellerName, item, price, expiresAt, raw, false, false, 0, 0L);
    }

    public AuctionListing(String id, UUID sellerUUID, String sellerName,
                          ItemStack item, double price, long expiresAt, boolean raw,
                          boolean autoRenew, boolean topSlot, int topSlotDays, long topSlotExpiresAt) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.expiresAt = expiresAt;
        this.autoRenew = autoRenew;
        this.topSlot = topSlot;
        this.topSlotDays = topSlotDays;
        this.topSlotExpiresAt = topSlotExpiresAt;
    }

    // ── Геттеры ───────────────────────────────────────────────────────────
    public String getId() { return id; }
    public UUID getSellerUUID() { return sellerUUID; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItem() { return item.clone(); }
    public double getPrice() { return price; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isAutoRenew() { return autoRenew; }
    public boolean isTopSlot() { return topSlot && System.currentTimeMillis() < topSlotExpiresAt; }
    public long getTopSlotExpiresAt() { return topSlotExpiresAt; }
    public int getTopSlotDays() { return topSlotDays; }

    /** Продлить авто-лот на ещё одни дефолтные сутки */
    public void renewExpiry(long durationMillis) {
        this.expiresAt = System.currentTimeMillis() + durationMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingMillis() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public String getFormattedTimeLeft() {
        if (autoRenew) return "∞ авто";
        long millis = getRemainingMillis();
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours   = minutes / 60;
        long days    = hours / 24;

        if (days > 0)    return days + "д " + (hours % 24) + "ч";
        if (hours > 0)   return hours + "ч " + (minutes % 60) + "м";
        if (minutes > 0) return minutes + "м " + (seconds % 60) + "с";
        return seconds + "с";
    }
}
