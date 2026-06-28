package dev.auction.auctionhouse.gui;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AuctionGUI {

    private static final int ROWS = 6;
    private static final int ITEMS_PER_PAGE = 45; // rows 1-5
    private static final int INV_SIZE = ROWS * 9; // 54

    // Bottom bar slots (row 6)
    private static final int SLOT_PREV   = 45;
    private static final int SLOT_INFO   = 49;
    private static final int SLOT_NEXT   = 53;
    private static final int SLOT_UNCLAIMED = 47;
    private static final int SLOT_MYLIST = 51;

    private final AuctionHouse plugin;

    public AuctionGUI(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<AuctionListing> listings = plugin.getAuctionManager().getAllListings();
        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, totalPages));

        String title = ChatColor.GOLD + "" + ChatColor.BOLD + "Auction House " +
                ChatColor.GRAY + "(Page " + page + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        // Fill item slots
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < listings.size(); i++) {
            AuctionListing listing = listings.get(startIndex + i);
            inv.setItem(i, buildListingItem(listing));
        }

        // Fill empty item slots with gray glass
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, buildGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        // Bottom bar - full black glass background
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, buildGlass(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        // Navigation
        if (page > 1) {
            inv.setItem(SLOT_PREV, buildNavItem(Material.ARROW,
                    ChatColor.YELLOW + "◀ Previous Page",
                    ChatColor.GRAY + "Go to page " + (page - 1)));
        }
        if (page < totalPages) {
            inv.setItem(SLOT_NEXT, buildNavItem(Material.ARROW,
                    ChatColor.YELLOW + "Next Page ▶",
                    ChatColor.GRAY + "Go to page " + (page + 1)));
        }

        // Info item
        inv.setItem(SLOT_INFO, buildNavItem(Material.GOLD_INGOT,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Auction House",
                ChatColor.GRAY + "Total listings: " + ChatColor.WHITE + listings.size(),
                ChatColor.GRAY + "Your listings: " + ChatColor.WHITE +
                        plugin.getAuctionManager().countListingsBySeller(player.getUniqueId()),
                ChatColor.GRAY + "Tax: " + ChatColor.WHITE +
                        plugin.getConfig().getInt("tax-percent") + "%",
                "",
                ChatColor.YELLOW + "Use /ahsell <price> to list an item"));

        // My listings button
        inv.setItem(SLOT_MYLIST, buildNavItem(Material.CHEST,
                ChatColor.AQUA + "My Listings",
                ChatColor.GRAY + "View your active listings"));

        // Unclaimed button
        boolean hasUnclaimed = plugin.getAuctionManager().hasUnclaimed(player.getUniqueId());
        inv.setItem(SLOT_UNCLAIMED, buildNavItem(
                hasUnclaimed ? Material.HOPPER : Material.BARREL,
                hasUnclaimed ? ChatColor.GREEN + "⚡ Unclaimed Items!" : ChatColor.GRAY + "Unclaimed Items",
                hasUnclaimed
                        ? ChatColor.GREEN + "You have items waiting!"
                        : ChatColor.GRAY + "No unclaimed items."));

        player.openInventory(inv);
    }

    public void openMyListings(Player player) {
        List<AuctionListing> myListings = plugin.getAuctionManager()
                .getListingsBySeller(player.getUniqueId());

        String title = ChatColor.AQUA + "" + ChatColor.BOLD + "My Listings";
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        for (int i = 0; i < Math.min(myListings.size(), ITEMS_PER_PAGE); i++) {
            AuctionListing listing = myListings.get(i);
            inv.setItem(i, buildMyListingItem(listing));
        }

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, buildGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, buildGlass(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(SLOT_INFO, buildNavItem(Material.ARROW,
                ChatColor.YELLOW + "◀ Back to Auction House",
                ChatColor.GRAY + "Return to main listing"));

        player.openInventory(inv);
    }

    public void openUnclaimed(Player player) {
        List<ItemStack> items = plugin.getAuctionManager().getUnclaimed(player.getUniqueId());

        String title = ChatColor.GREEN + "" + ChatColor.BOLD + "Unclaimed Items";
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        for (int i = 0; i < Math.min(items.size(), ITEMS_PER_PAGE); i++) {
            ItemStack display = items.get(i).clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GREEN + "➤ Click to claim!");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(i, display);
        }

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, buildGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, buildGlass(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(SLOT_INFO, buildNavItem(Material.ARROW,
                ChatColor.YELLOW + "◀ Back to Auction House",
                ChatColor.GRAY + "Return to main listing"));

        player.openInventory(inv);
    }

    // ─────────────────────────── ITEM BUILDERS ────────────────────────────

    private ItemStack buildListingItem(AuctionListing listing) {
        ItemStack item = listing.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = meta.hasDisplayName()
                ? meta.getDisplayName()
                : ChatColor.WHITE + plugin.getAuctionManager().getItemName(item);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Seller: " + ChatColor.YELLOW + listing.getSellerName());
        lore.add(ChatColor.GRAY + "Price:  " + ChatColor.GREEN +
                plugin.getConfig().getString("currency-symbol", "$") +
                String.format("%,.2f", listing.getPrice()));
        lore.add(ChatColor.GRAY + "Expires: " + ChatColor.WHITE + listing.getFormattedTimeLeft());
        lore.add(ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY + listing.getId());
        lore.add("");
        lore.add(ChatColor.YELLOW + "➤ Left-click to purchase");

        // Preserve original lore
        if (meta.hasLore()) {
            lore.add(ChatColor.DARK_GRAY + "──── Item Lore ────");
            lore.addAll(meta.getLore());
        }

        meta.setDisplayName(displayName);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildMyListingItem(AuctionListing listing) {
        ItemStack item = listing.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = meta.hasDisplayName()
                ? meta.getDisplayName()
                : ChatColor.WHITE + plugin.getAuctionManager().getItemName(item);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Price:   " + ChatColor.GREEN +
                plugin.getConfig().getString("currency-symbol", "$") +
                String.format("%,.2f", listing.getPrice()));
        lore.add(ChatColor.GRAY + "Expires: " + ChatColor.WHITE + listing.getFormattedTimeLeft());
        lore.add(ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY + listing.getId());
        lore.add("");
        lore.add(ChatColor.RED + "➤ Right-click to cancel & retrieve");

        meta.setDisplayName(displayName);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildGlass(Material mat, String name) {
        ItemStack glass = new ItemStack(mat);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(name);
        glass.setItemMeta(meta);
        return glass;
    }

    private ItemStack buildNavItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>(List.of(loreLines));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
