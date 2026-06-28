package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AHSellCommand implements CommandExecutor {

    private final AuctionHouse plugin;

    public AHSellCommand(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("auctionhouse.sell")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.msg("prefix") + "§eUsage: /ahsell <price> [amount]");
            return true;
        }

        double minPrice = plugin.getConfig().getDouble("min-price", 1.0);
        double maxPrice = plugin.getConfig().getDouble("max-price", 10_000_000.0);

        double price;
        try {
            price = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.msg("invalid-price")
                    .replace("{min}", String.format("%,.0f", minPrice))
                    .replace("{max}", String.format("%,.0f", maxPrice)));
            return true;
        }

        if (price < minPrice || price > maxPrice) {
            player.sendMessage(plugin.msg("invalid-price")
                    .replace("{min}", String.format("%,.0f", minPrice))
                    .replace("{max}", String.format("%,.0f", maxPrice)));
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(plugin.msg("no-item"));
            return true;
        }

        // Optional: specific amount from arg
        int amount = hand.getAmount();
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                amount = Math.max(1, Math.min(amount, hand.getAmount()));
            } catch (NumberFormatException ignored) {}
        }

        // Check listing limit
        int maxListings = plugin.getConfig().getInt("max-listings-per-player", 10);
        if (maxListings != -1 && !player.hasPermission("auctionhouse.bypass.limit")) {
            int current = plugin.getAuctionManager().countListingsBySeller(player.getUniqueId());
            if (current >= maxListings) {
                player.sendMessage(plugin.msg("listing-limit").replace("{max}", String.valueOf(maxListings)));
                return true;
            }
        }

        // Take item from player
        ItemStack toSell = hand.clone();
        toSell.setAmount(amount);

        if (hand.getAmount() == amount) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - amount);
        }

        // Create listing
        long durationMillis = plugin.getConfig().getLong("default-duration-minutes", 1440) * 60_000L;
        String id = plugin.getAuctionManager().generateId();

        AuctionListing listing = new AuctionListing(
                id,
                player.getUniqueId(),
                player.getName(),
                toSell,
                price,
                durationMillis
        );

        plugin.getAuctionManager().addListing(listing);
        plugin.getAuctionManager().save();

        String currency = plugin.getConfig().getString("currency-symbol", "$");
        player.sendMessage(plugin.msg("item-listed")
                .replace("{item}", plugin.getAuctionManager().getItemName(toSell))
                .replace("{price}", currency + String.format("%,.2f", price)));

        return true;
    }
}
