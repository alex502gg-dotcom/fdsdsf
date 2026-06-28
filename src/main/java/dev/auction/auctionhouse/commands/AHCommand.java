package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.gui.AuctionGUI;
import dev.auction.auctionhouse.gui.GUIListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AHCommand implements CommandExecutor {

    private final AuctionHouse plugin;

    public AHCommand(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("auctionhouse.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        new AuctionGUI(plugin).open(player, 1);
        return true;
    }
}
