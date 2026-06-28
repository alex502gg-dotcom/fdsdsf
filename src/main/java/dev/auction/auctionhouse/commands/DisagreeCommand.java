package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.PendingSell;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class DisagreeCommand implements CommandExecutor {

    private final AuctionHouse plugin;

    public DisagreeCommand(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        UUID uid = player.getUniqueId();
        PendingSell pending = plugin.getPendingSells().remove(uid);

        if (pending == null) {
            player.sendMessage(plugin.msgRaw("prefix") + "§cНет ожидающей продажи.");
            return true;
        }

        // Возвращаем предметы
        for (Map.Entry<ItemStack, Integer> e : pending.getStacks().entrySet()) {
            ItemStack item = e.getKey().clone();
            item.setAmount(e.getValue());
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.forEach((i, left) ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
        }

        player.sendMessage(plugin.msgRaw("prefix") + "§aПродажа отменена. Предметы возвращены.");
        return true;
    }
}
