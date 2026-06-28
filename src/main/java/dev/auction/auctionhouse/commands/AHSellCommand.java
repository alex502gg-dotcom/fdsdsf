package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import dev.auction.auctionhouse.model.PendingSell;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * /ahsell <цена> [hand [all|<N>]] [all] [--days N] [--auto] [--top N]
 *
 *   /ahsell 500                   → продать стак в руке
 *   /ahsell 500 hand 10           → продать 10 шт из руки
 *   /ahsell 500 hand all          → продать все предметы того же типа
 *   /ahsell 500 all               → продать ВЕСЬ инвентарь (требует /agree)
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

        // ── Парсим флаги ─────────────────────────────────────────────────
        List<String> extra = new ArrayList<>();
        for (int i = 1; i < args.length; i++) extra.add(args[i].toLowerCase());

        boolean sellAll       = false;  // весь инвентарь
        boolean handAll       = false;  // все предметы типа из руки
        int specificAmount    = -1;
        int customDays        = 0;
        int topDays           = 0;
        boolean autoRenew     = false;

        // hand [all | <N>]
        int handIdx = extra.indexOf("hand");
        if (handIdx >= 0) {
            if (handIdx + 1 < extra.size()) {
                String next = extra.get(handIdx + 1);
                if (next.equals("all")) {
                    handAll = true;
                } else {
                    try {
                        specificAmount = Integer.parseInt(next);
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.msgRaw("prefix") + "§cНеверное количество: " + next);
                        return true;
                    }
                }
            }
        }

        // all (без hand) → весь инвентарь
        if (extra.contains("all") && handIdx < 0) {
            sellAll = true;
        }

        // --days <N>
        int daysIdx = extra.indexOf("--days");
        if (daysIdx >= 0 && daysIdx + 1 < extra.size()) {
            try { customDays = Math.max(1, Integer.parseInt(extra.get(daysIdx + 1))); }
            catch (NumberFormatException ignored) {}
        }

        // --top <N>
        int topIdx = extra.indexOf("--top");
        if (topIdx >= 0 && topIdx + 1 < extra.size()) {
            try { topDays = Math.max(1, Integer.parseInt(extra.get(topIdx + 1))); }
            catch (NumberFormatException ignored) {}
        }

        // --auto
        if (extra.contains("--auto")) autoRenew = true;

        // ── Стоимости платных опций ──────────────────────────────────────
        double dayCost    = plugin.getConfig().getDouble("custom-day-cost", 10_000.0);
        double autoCost   = plugin.getConfig().getDouble("auto-renew-cost", 100_000.0);
        double topDayCost = plugin.getConfig().getDouble("top-slot-day-cost", 10_000.0);
        double extraCost  = 0;
        if (customDays > 1) extraCost += dayCost * customDays;
        if (autoRenew)      extraCost += autoCost;
        if (topDays > 0)    extraCost += topDayCost * topDays;

        long defaultDuration = plugin.getConfig().getLong("default-duration-minutes", 1440) * 60_000L;
        long durationMillis  = (customDays > 0) ? (long) customDays * 86_400_000L : defaultDuration;

        // ════════════════════════════════════════════════════════════════
        //  РЕЖИМ: весь инвентарь (/ahsell <цена> all)
        // ════════════════════════════════════════════════════════════════
        if (sellAll) {
            // Собираем карту: шаблон предмета → суммарное кол-во
            Map<ItemStack, Integer> stacks = collectInventory(player);

            if (stacks.isEmpty()) {
                player.sendMessage(plugin.msgRaw("prefix") + "§cВаш инвентарь пуст!");
                return true;
            }

            // Считаем сколько лотов получится
            int lotCount = countLots(stacks);

            // Проверяем лимит
            int maxListings = plugin.getConfig().getInt("max-listings-per-player", 10);
            if (maxListings != -1 && !player.hasPermission("auctionhouse.bypass.limit")) {
                int current = plugin.getAuctionManager().countListingsBySeller(player.getUniqueId());
                if (current + lotCount > maxListings) {
                    player.sendMessage(plugin.msg("listing-limit").replace("{max}", String.valueOf(maxListings)));
                    return true;
                }
            }

            // Сохраняем pending и просим подтверждения
            PendingSell pending = new PendingSell(stacks, price, durationMillis,
                    autoRenew, topDays > 0, topDays, extraCost);
            plugin.getPendingSells().put(player.getUniqueId(), pending);

            // Забираем предметы из инвентаря сразу (до /agree)
            // чтобы игрок не мог продать то же самое дважды
            removeInventory(player, stacks);

            String currency = plugin.getConfig().getString("currency-symbol", "$");
            player.sendMessage(plugin.msgRaw("prefix") + "§eВы собираетесь выставить §f" + lotCount +
                    " §eлот(ов) за §f" + currency + String.format("%,.2f", price) + " §eкаждый.");
            player.sendMessage(plugin.msgRaw("prefix") + "§aВведите §f/agree §aдля подтверждения " +
                    "§7(или §c/disagree §7для отмены)§a. §7У вас §e30 секунд§7.");

            // Авто-отмена через 30 секунд если не подтвердил
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PendingSell current2 = plugin.getPendingSells().remove(player.getUniqueId());
                if (current2 != null) {
                    // Возвращаем предметы
                    returnStacks(player, current2.getStacks());
                    if (player.isOnline()) {
                        player.sendMessage(plugin.msgRaw("prefix") +
                                "§cВремя вышло. Предметы возвращены в инвентарь.");
                    }
                }
            }, 600L); // 30 сек = 600 тиков

            return true;
        }

        // ════════════════════════════════════════════════════════════════
        //  РЕЖИМ: предмет в руке
        // ════════════════════════════════════════════════════════════════
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(plugin.msg("no-item"));
            return true;
        }

        int amountToSell;
        if (handAll) {
            amountToSell = countSameItems(player, hand);
        } else if (specificAmount > 0) {
            amountToSell = Math.min(specificAmount, hand.getAmount());
        } else {
            amountToSell = hand.getAmount();
        }

        if (amountToSell <= 0) {
            player.sendMessage(plugin.msg("no-item"));
            return true;
        }

        // Лимит
        int maxListings = plugin.getConfig().getInt("max-listings-per-player", 10);
        if (maxListings != -1 && !player.hasPermission("auctionhouse.bypass.limit")) {
            int current = plugin.getAuctionManager().countListingsBySeller(player.getUniqueId());
            if (current >= maxListings) {
                player.sendMessage(plugin.msg("listing-limit").replace("{max}", String.valueOf(maxListings)));
                return true;
            }
        }

        // Оплата опций
        if (extraCost > 0 && !plugin.getEconomyManager().has(player, extraCost)) {
            String curr = plugin.getConfig().getString("currency-symbol", "$");
            player.sendMessage(plugin.msgRaw("prefix") +
                    "§cНедостаточно денег для платных опций! Нужно: §e" +
                    curr + String.format("%,.0f", extraCost));
            return true;
        }
        if (extraCost > 0) plugin.getEconomyManager().withdraw(player, extraCost);

        // Забираем предметы
        ItemStack toSell;
        if (handAll) {
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

        String id = plugin.getAuctionManager().generateId();
        AuctionListing listing = new AuctionListing(
                id, player.getUniqueId(), player.getName(),
                toSell, price, durationMillis, autoRenew, topDays > 0, topDays
        );

        plugin.getAuctionManager().addListing(listing);
        plugin.getAuctionManager().save();

        String currency = plugin.getConfig().getString("currency-symbol", "$");
        StringBuilder msg = new StringBuilder(plugin.msg("item-listed")
                .replace("{item}", plugin.getAuctionManager().getItemName(toSell) +
                        (amountToSell > 1 ? " §7x" + amountToSell : ""))
                .replace("{price}", currency + String.format("%,.2f", price)));

        if (autoRenew)   msg.append(" §d[Авто-продление]");
        if (topDays > 0) msg.append(" §6[Топ " + topDays + "д]");
        if (customDays > 1) msg.append(" §b[" + customDays + " дней]");

        player.sendMessage(msg.toString());
        return true;
    }

    // ── Хелперы ──────────────────────────────────────────────────────────

    /** Собирает весь инвентарь: шаблон → суммарное кол-во */
    private Map<ItemStack, Integer> collectInventory(Player player) {
        Map<ItemStack, Integer> result = new LinkedHashMap<>();
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) continue;
            ItemStack key = slot.clone();
            key.setAmount(1);
            result.merge(key, slot.getAmount(), Integer::sum);
        }
        return result;
    }

    /** Считает сколько лотов получится (с учётом maxStackSize) */
    private int countLots(Map<ItemStack, Integer> stacks) {
        int total = 0;
        for (Map.Entry<ItemStack, Integer> e : stacks.entrySet()) {
            int max = e.getKey().getMaxStackSize();
            total += (int) Math.ceil((double) e.getValue() / max);
        }
        return total;
    }

    /** Удаляет все предметы из инвентаря по карте */
    private void removeInventory(Player player, Map<ItemStack, Integer> stacks) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (Map.Entry<ItemStack, Integer> entry : stacks.entrySet()) {
            ItemStack template = entry.getKey();
            int remaining = entry.getValue();
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
        }
        player.getInventory().setStorageContents(contents);
    }

    /** Возвращает предметы игроку (при отмене/таймауте) */
    private void returnStacks(Player player, Map<ItemStack, Integer> stacks) {
        for (Map.Entry<ItemStack, Integer> e : stacks.entrySet()) {
            ItemStack item = e.getKey().clone();
            item.setAmount(e.getValue());
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.forEach((i, left) ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private int countSameItems(Player player, ItemStack sample) {
        int count = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot != null && slot.isSimilar(sample)) count += slot.getAmount();
        }
        return count;
    }

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
        String p = plugin.msgRaw("prefix");
        player.sendMessage(p + "§eИспользование:");
        player.sendMessage(p + "§f/ahsell <цена>§7 — продать стак в руке");
        player.sendMessage(p + "§f/ahsell <цена> hand <N>§7 — продать N шт. из руки");
        player.sendMessage(p + "§f/ahsell <цена> hand all§7 — продать все предметы типа");
        player.sendMessage(p + "§f/ahsell <цена> all§7 — продать весь инвентарь (нужен §f/agree§7)");
        player.sendMessage(p + "§fФлаги: §b--days N §7(10к/сут)§f, --auto §7(100к/мес)§f, --top N §7(10к/сут)");
    }
}
