package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import dev.auction.auctionhouse.model.PendingSell;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class AgreeCommand implements CommandExecutor {

    private final AuctionHouse plugin;

    public AgreeCommand(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        UUID uid = player.getUniqueId();
        PendingSell pending = plugin.getPendingSells().get(uid);

        if (pending == null) {
            player.sendMessage(plugin.msgRaw("prefix") + "§cНет ожидающего подтверждения продажи.");
            return true;
        }

        // Удаляем из ожидания сразу чтобы не спамили
        plugin.getPendingSells().remove(uid);

        // Проверяем истечение (30 секунд)
        if (System.currentTimeMillis() - pending.getCreatedAt() > 30_000L) {
            player.sendMessage(plugin.msgRaw("prefix") + "§cВремя подтверждения истекло. Повторите команду.");
            return true;
        }

        // Лимит листингов
        int maxListings = plugin.getConfig().getInt("max-listings-per-player", 10);
        if (maxListings != -1 && !player.hasPermission("auctionhouse.bypass.limit")) {
            int current = plugin.getAuctionManager().countListingsBySeller(uid);
            if (current >= maxListings) {
                player.sendMessage(plugin.msg("listing-limit").replace("{max}", String.valueOf(maxListings)));
                return true;
            }
        }

        // Списываем деньги за доп. опции если есть
        double extraCost = pending.getExtraCost();
        if (extraCost > 0) {
            if (!plugin.getEconomyManager().has(player, extraCost)) {
                String curr = plugin.getConfig().getString("currency-symbol", "$");
                player.sendMessage(plugin.msgRaw("prefix") +
                        "§cНедостаточно денег для платных опций! Нужно: §e" +
                        curr + String.format("%,.0f", extraCost));
                // Возвращаем предметы
                for (Map.Entry<ItemStack, Integer> e : pending.getStacks().entrySet()) {
                    ItemStack ret = e.getKey().clone();
                    ret.setAmount(e.getValue());
                    player.getInventory().addItem(ret);
                }
                return true;
            }
            plugin.getEconomyManager().withdraw(player, extraCost);
        }

        // Снимаем предметы с игрока и выставляем каждый стак отдельным лотом
        String currency = plugin.getConfig().getString("currency-symbol", "$");
        int count = 0;

        for (Map.Entry<ItemStack, Integer> entry : pending.getStacks().entrySet()) {
            ItemStack template = entry.getKey();
            int totalAmount = entry.getValue();
            int maxStack = template.getMaxStackSize();

            // Снимаем предметы
            removeFromInventory(player, template, totalAmount);

            // Разбиваем на стаки если нужно
            while (totalAmount > 0) {
                int stackSize = Math.min(totalAmount, maxStack);
                ItemStack toSell = template.clone();
                toSell.setAmount(stackSize);
                totalAmount -= stackSize;

                String id = plugin.getAuctionManager().generateId();
                AuctionListing listing = new AuctionListing(
                        id,
                        uid,
                        player.getName(),
                        toSell,
                        pending.getPrice(),
                        pending.getDurationMillis(),
                        pending.isAutoRenew(),
                        pending.isTopSlot(),
                        pending.getTopDays()
                );
                plugin.getAuctionManager().addListing(listing);
                count++;
            }
        }

        plugin.getAuctionManager().save();

        player.sendMessage(plugin.msgRaw("prefix") + "§aВесь инвентарь выставлен! §e" +
                count + " §aлот(ов) за §e" + currency + String.format("%,.2f", pending.getPrice()) + " §aкаждый.");
        return true;
    }

    private void removeFromInventory(Player player, ItemStack template, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack slot = contents[i];
            if (slot != null && slot.isSimilar(template)) {
                if (slot.getAmount() <= remaining) {
                    remaining -= slot.getAmount();
                    contents[i] = null;
                } else {
                    slot.setAmount(slot.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
        player.getInventory().setStorageContents(contents);
    }
}
