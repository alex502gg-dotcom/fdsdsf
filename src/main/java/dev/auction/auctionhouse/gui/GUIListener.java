package dev.auction.auctionhouse.gui;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.manager.AuctionManager;
import dev.auction.auctionhouse.manager.EconomyManager;
import dev.auction.auctionhouse.model.AuctionListing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;

public class GUIListener implements Listener {

    private static final String MAIN_TITLE_PREFIX = ChatColor.GOLD + "" + ChatColor.BOLD + "Auction House";
    private static final String MY_TITLE   = ChatColor.AQUA + "" + ChatColor.BOLD + "My Listings";
    private static final String UNC_TITLE  = ChatColor.GREEN + "" + ChatColor.BOLD + "Unclaimed Items";

    private final AuctionHouse plugin;
    private final AuctionGUI gui;

    public GUIListener(AuctionHouse plugin) {
        this.plugin = plugin;
        this.gui = new AuctionGUI(plugin);
    }

    public AuctionGUI getGui() { return gui; }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.startsWith(MAIN_TITLE_PREFIX)) {
            event.setCancelled(true);
            handleMainGUI(player, event.getSlot(), title);
        } else if (title.equals(MY_TITLE)) {
            event.setCancelled(true);
            handleMyListings(player, event.getSlot(), event.getClick().isRightClick());
        } else if (title.equals(UNC_TITLE)) {
            event.setCancelled(true);
            handleUnclaimed(player, event.getSlot());
        }
    }

    // ─────────────────── MAIN GUI HANDLER ──────────────────────

    private void handleMainGUI(Player player, int slot, String title) {
        // Parse current page from title "... (Page X/Y)"
        int page = parsePageFromTitle(title);

        if (slot == 49) return; // info – do nothing
        if (slot == 51) { // my listings
            gui.openMyListings(player);
            return;
        }
        if (slot == 47) { // unclaimed
            gui.openUnclaimed(player);
            return;
        }
        if (slot == 45) { // prev page
            gui.open(player, page - 1);
            return;
        }
        if (slot == 53) { // next page
            gui.open(player, page + 1);
            return;
        }

        // Clicked an item slot (0-44)
        if (slot < 0 || slot >= 45) return;
        ItemStack clicked = player.getOpenInventory().getItem(slot);
        if (clicked == null || !clicked.hasItemMeta()) return;

        // Extract listing ID from lore
        String id = extractId(clicked);
        if (id == null) return;

        Optional<AuctionListing> opt = plugin.getAuctionManager().getListing(id);
        if (opt.isEmpty()) {
            player.sendMessage(plugin.msg("auction-not-found"));
            gui.open(player, page);
            return;
        }
        AuctionListing listing = opt.get();

        // Can't buy own auction
        if (listing.getSellerUUID().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("own-auction"));
            return;
        }

        // Check balance
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.has(player, listing.getPrice())) {
            player.sendMessage(plugin.msg("not-enough-money"));
            return;
        }

        // Purchase
        plugin.getAuctionManager().removeListing(id);
        eco.withdraw(player, listing.getPrice());

        // Calculate payout after tax
        double taxPercent = plugin.getConfig().getDouble("tax-percent", 5.0);
        double tax = listing.getPrice() * (taxPercent / 100.0);
        double payout = listing.getPrice() - tax;

        // Pay seller
        eco.deposit(Bukkit.getOfflinePlayer(listing.getSellerUUID()), payout);

        // Give item to buyer
        if (!player.getInventory().addItem(listing.getItem()).isEmpty()) {
            plugin.getAuctionManager().addUnclaimed(player.getUniqueId(), listing.getItem());
            player.sendMessage(plugin.msg("inventory-full"));
        }

        String currency = plugin.getConfig().getString("currency-symbol", "$");
        player.sendMessage(plugin.msg("item-purchased")
                .replace("{item}", plugin.getAuctionManager().getItemName(listing.getItem()))
                .replace("{price}", currency + String.format("%,.2f", listing.getPrice())));

        // Notify seller if online
        Player seller = Bukkit.getPlayer(listing.getSellerUUID());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(plugin.msg("item-sold")
                    .replace("{item}", plugin.getAuctionManager().getItemName(listing.getItem()))
                    .replace("{price}", currency + String.format("%,.2f", payout))
                    .replace("{tax}", currency + String.format("%,.2f", tax)));
        }

        plugin.getAuctionManager().save();
        gui.open(player, page);
    }

    // ─────────────────── MY LISTINGS HANDLER ───────────────────

    private void handleMyListings(Player player, int slot, boolean rightClick) {
        if (slot == 49) { // back button
            gui.open(player, 1);
            return;
        }

        if (slot < 0 || slot >= 45) return;
        if (!rightClick) return; // only right-click cancels

        ItemStack clicked = player.getOpenInventory().getItem(slot);
        if (clicked == null || !clicked.hasItemMeta()) return;

        String id = extractId(clicked);
        if (id == null) return;

        Optional<AuctionListing> opt = plugin.getAuctionManager().getListing(id);
        if (opt.isEmpty()) {
            player.sendMessage(plugin.msg("auction-not-found"));
            gui.openMyListings(player);
            return;
        }

        AuctionListing listing = opt.get();
        plugin.getAuctionManager().removeListing(listing.getId());

        if (!player.getInventory().addItem(listing.getItem()).isEmpty()) {
            plugin.getAuctionManager().addUnclaimed(player.getUniqueId(), listing.getItem());
            player.sendMessage(plugin.msg("inventory-full"));
        } else {
            player.sendMessage(plugin.msg("auction-cancelled"));
        }

        plugin.getAuctionManager().save();
        gui.openMyListings(player);
    }

    // ─────────────────── UNCLAIMED HANDLER ─────────────────────

    private void handleUnclaimed(Player player, int slot) {
        if (slot == 49) { // back
            gui.open(player, 1);
            return;
        }

        if (slot < 0 || slot >= 45) return;
        ItemStack clicked = player.getOpenInventory().getItem(slot);
        if (clicked == null || clicked.getType().isAir()) return;

        List<ItemStack> items = plugin.getAuctionManager().getUnclaimed(player.getUniqueId());
        if (slot >= items.size()) return;

        ItemStack original = items.get(slot);
        if (player.getInventory().addItem(original).isEmpty()) {
            items.remove(slot);
            if (items.isEmpty()) {
                plugin.getAuctionManager().clearUnclaimed(player.getUniqueId());
            }
            plugin.getAuctionManager().save();
        } else {
            player.sendMessage(plugin.msg("inventory-full"));
        }

        gui.openUnclaimed(player);
    }

    // ─────────────────── HELPERS ───────────────────────────────

    private String extractId(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return null;
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && stripped.startsWith("ID: ")) {
                return stripped.substring(4).trim();
            }
        }
        return null;
    }

    private int parsePageFromTitle(String title) {
        // Title format: "Auction House (Page X/Y)"
        try {
            int paren = title.lastIndexOf("(Page ");
            if (paren == -1) return 1;
            int slash = title.indexOf('/', paren);
            return Integer.parseInt(title.substring(paren + 6, slash).trim());
        } catch (Exception e) {
            return 1;
        }
    }
}
