package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AHReloadCommand implements CommandExecutor {

    private final AuctionHouse plugin;

    public AHReloadCommand(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auctionhouse.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.reloadConfig();
        sender.sendMessage(plugin.msg("plugin-reloaded"));
        return true;
    }
}
