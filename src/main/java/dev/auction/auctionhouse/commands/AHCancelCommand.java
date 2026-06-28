package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public class AHCancelCommand implements CommandExecutor {

    private final AuctionHouse plugin;

    public AHCancelCommand(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.msg("prefix") + "§eUsage: /ahcancel <id>");
            return true;
        }

        String id = args[0].toUpperCase();
        Optional<AuctionListing> opt = plugin.getAuctionManager().getListing(id);

        if (opt.isEmpty()) {
            player.sendMessage(plugin.msg("auction-not-found"));
            return true;
        }

        AuctionListing listing = opt.get();

        // Must be owner or admin
        if (!listing.getSellerUUID().equals(player.getUniqueId())
                && !player.hasPermission("auctionhouse.admin")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        plugin.getAuctionManager().removeListing(id);

        if (!player.getInventory().addItem(listing.getItem()).isEmpty()) {
            plugin.getAuctionManager().addUnclaimed(player.getUniqueId(), listing.getItem());
            player.sendMessage(plugin.msg("inventory-full"));
        } else {
            player.sendMessage(plugin.msg("auction-cancelled"));
        }

        plugin.getAuctionManager().save();
        return true;
    }
}
