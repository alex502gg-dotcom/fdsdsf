package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /ahsell <цена> [hand [all|<кол-во>]] [--days <N>] [--top <N>] [--auto]
 *
 * Примеры:
 *   /ahsell 500                       → продать 1 предмет из руки (как раньше)
 *   /ahsell 500 hand 10               → продать 10 шт из руки
 *   /ahsell 500 hand all              → продать все предметы того типа из руки
 *   /ahsell 500 --days 7              → лот стоит 7 дней (10 000/сут)
 *   /ahsell 500 --auto                → авто-продление (100 000/месяц)
 *   /ahsell 500 --top 3               → топ-позиция 3 дня (10 000/сут)
 *   /ahsell 500 hand all --days 7 --top 2 --auto
 */
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
            sendUsage(player);
            return true;
        }

        // ── Парсим цену ──────────────────────────────────────────────────
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

        // ── Предмет в руке ───────────────────────────────────────────────
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(plugin.msg("no-item"));
            return true;
        }

        // ── Парсим флаги ─────────────────────────────────────────────────
        boolean sellAll = false;
        int specificAmount = -1; // -1 = не задано
        int customDays = 0;      // 0 = дефолт
        int topDays = 0;
        boolean autoRenew = false;

        // Собираем аргументы после [0]
        List<String> extra = new ArrayList<>();
        for (int i = 1; i < args.length; i++) extra.add(args[i].toLowerCase());

        // hand [all | <N>]
        int handIdx = extra.indexOf("hand");
        if (handIdx >= 0) {
            if (handIdx + 1 < extra.size()) {
                String next = extra.get(handIdx + 1);
                if (next.equals("all")) {
                    sellAll = true;
                } else {
                    try {
                        specificAmount = Integer.parseInt(next);
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.msgRaw("prefix") +
                                "§cНеверное количество: " + next);
                        return true;
                    }
                }
            } else {
                // /ahsell <цена> hand — без уточнения = 1 шт (как обычно)
            }
        }

        // --days <N>
        int daysIdx = extra.indexOf("--days");
        if (daysIdx >= 0 && daysIdx + 1 < extra.size()) {
            try {
                customDays = Integer.parseInt(extra.get(daysIdx + 1));
                if (customDays < 1) customDays = 1;
            } catch (NumberFormatException ignored) {}
        }

        // --top <N>
        int topIdx = extra.indexOf("--top");
        if (topIdx >= 0 && topIdx + 1 < extra.size()) {
            try {
                topDays = Integer.parseInt(extra.get(topIdx + 1));
                if (topDays < 1) topDays = 1;
            } catch (NumberFormatException ignored) {}
        }

        // --auto
        if (extra.contains("--auto")) autoRenew = true;

        // ── Вычисляем сколько предметов продаём ─────────────────────────
        int amountToSell;
        if (sellAll) {
            // Считаем все предметы того же типа/мета в инвентаре
            amountToSell = countSameItems(player, hand);
        } else if (specificAmount > 0) {
            amountToSell = Math.min(specificAmount, hand.getAmount());
        } else {
            // Стандарт: весь стак что в руке (или 1 — как было раньше)
            // Оставляем поведение как было: берём всё что в руке
            amountToSell = hand.getAmount();
        }

        if (amountToSell <= 0) {
            player.sendMessage(plugin.msg("no-item"));
            return true;
        }

        // ── Лимит листингов ─────────────────────────────────────────────
        int maxListings = plugin.getConfig().getInt("max-listings-per-player", 10);
        if (maxListings != -1 && !player.hasPermission("auctionhouse.bypass.limit")) {
            int current = plugin.getAuctionManager().countListingsBySeller(player.getUniqueId());
            if (current >= maxListings) {
                player.sendMessage(plugin.msg("listing-limit").replace("{max}", String.valueOf(maxListings)));
                return true;
            }
        }

        // ── Стоимость платных услуг ─────────────────────────────────────
        double dayCost = plugin.getConfig().getDouble("custom-day-cost", 10_000.0);
        double autoCost = plugin.getConfig().getDouble("auto-renew-cost", 100_000.0);
        double topDayCost = plugin.getConfig().getDouble("top-slot-day-cost", 10_000.0);

        double extraCost = 0;

        if (customDays > 1) {
            extraCost += dayCost * customDays; // платят за ВСЕ сутки
        }
        if (autoRenew) {
            extraCost += autoCost;
        }
        if (topDays > 0) {
            extraCost += topDayCost * topDays;
        }

        if (extraCost > 0 && !plugin.getEconomyManager().has(player, extraCost)) {
            String curr = plugin.getConfig().getString("currency-symbol", "$");
            player.sendMessage(plugin.msgRaw("prefix") +
                    "§cНедостаточно денег для платных опций! Нужно: §e" +
                    curr + String.format("%,.0f", extraCost));
            return true;
        }

        // Списываем деньги за опции
        if (extraCost > 0) {
            plugin.getEconomyManager().withdraw(player, extraCost);
        }

        // ── Забираем предметы ────────────────────────────────────────────
        ItemStack toSell;
        if (sellAll) {
            toSell = removeAllSameItems(player, hand, amountToSell);
        } else {
            toSell = hand.clone();
            toSell.setAmount(amountToSell);
            if (hand.getAmount() == amountToSell) {
                player.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(hand.getAmount() - amountToSell);
            }
        }

        // ── Длительность лота ────────────────────────────────────────────
        long defaultDurationMillis = plugin.getConfig().getLong("default-duration-minutes", 1440) * 60_000L;
        long durationMillis = (customDays > 0)
                ? (long) customDays * 86_400_000L
                : defaultDurationMillis;

        // ── Создаём лот ──────────────────────────────────────────────────
        String id = plugin.getAuctionManager().generateId();
        AuctionListing listing = new AuctionListing(
                id,
                player.getUniqueId(),
                player.getName(),
                toSell,
                price,
                durationMillis,
                autoRenew,
                topDays > 0,
                topDays
        );

        plugin.getAuctionManager().addListing(listing);
        plugin.getAuctionManager().save();

        String currency = plugin.getConfig().getString("currency-symbol", "$");
        StringBuilder msg = new StringBuilder(plugin.msg("item-listed")
                .replace("{item}", plugin.getAuctionManager().getItemName(toSell))
                .replace("{price}", currency + String.format("%,.2f", price)));

        if (autoRenew)  msg.append(" §d[Авто-продление]");
        if (topDays > 0) msg.append(" §6[Топ " + topDays + "д]");
        if (customDays > 1) msg.append(" §b[" + customDays + " дней]");

        player.sendMessage(msg.toString());
        return true;
    }

    /** Считает суммарное количество одинаковых предметов в инвентаре */
    private int countSameItems(Player player, ItemStack sample) {
        int count = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot != null && slot.isSimilar(sample)) {
                count += slot.getAmount();
            }
        }
        // Не забываем предмет в руке (он уже входит в getStorageContents)
        return count;
    }

    /** Удаляет все похожие предметы из инвентаря и возвращает один стак суммарным кол-вом */
    private ItemStack removeAllSameItems(Player player, ItemStack sample, int totalAmount) {
        int remaining = totalAmount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack slot = contents[i];
            if (slot != null && slot.isSimilar(sample)) {
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

        ItemStack result = sample.clone();
        result.setAmount(totalAmount);
        return result;
    }

    private void sendUsage(Player player) {
        String pref = plugin.msgRaw("prefix");
        player.sendMessage(pref + "§eИспользование:");
        player.sendMessage(pref + "§f/ahsell <цена>§7 — продать предмет в руке");
        player.sendMessage(pref + "§f/ahsell <цена> hand <кол-во>§7 — продать N шт.");
        player.sendMessage(pref + "§f/ahsell <цена> hand all§7 — продать весь тип из инвентаря");
        player.sendMessage(pref + "§fФлаги: §b--days <N>§7 (доп.дни, 10к/сут), §d--auto§7 (авто-продл., 100к/мес), §6--top <N>§7 (топ N дней, 10к/сут)");
    }
}
