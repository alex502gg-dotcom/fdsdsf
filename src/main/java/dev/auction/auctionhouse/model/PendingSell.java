package dev.auction.auctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Хранит данные ожидающей продажи всего инвентаря до подтверждения /agree.
 * Живёт 30 секунд.
 */
public class PendingSell {

    private final Map<ItemStack, Integer> stacks; // шаблон → суммарное кол-во
    private final double price;
    private final long durationMillis;
    private final boolean autoRenew;
    private final boolean topSlot;
    private final int topDays;
    private final double extraCost;
    private final long createdAt;

    public PendingSell(Map<ItemStack, Integer> stacks, double price, long durationMillis,
                       boolean autoRenew, boolean topSlot, int topDays, double extraCost) {
        this.stacks = stacks;
        this.price = price;
        this.durationMillis = durationMillis;
        this.autoRenew = autoRenew;
        this.topSlot = topSlot;
        this.topDays = topDays;
        this.extraCost = extraCost;
        this.createdAt = System.currentTimeMillis();
    }

    public Map<ItemStack, Integer> getStacks()    { return stacks; }
    public double getPrice()                       { return price; }
    public long getDurationMillis()                { return durationMillis; }
    public boolean isAutoRenew()                   { return autoRenew; }
    public boolean isTopSlot()                     { return topSlot; }
    public int getTopDays()                        { return topDays; }
    public double getExtraCost()                   { return extraCost; }
    public long getCreatedAt()                     { return createdAt; }
}
