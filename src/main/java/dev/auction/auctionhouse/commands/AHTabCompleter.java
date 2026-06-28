package dev.auction.auctionhouse.commands;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AHTabCompleter implements TabCompleter {

    private final AuctionHouse plugin;

    public AHTabCompleter(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        String cmd = command.getName().toLowerCase();

        return switch (cmd) {
            case "ahsell" -> completeAhsell(player, args);
            case "ahcancel" -> completeAhcancel(player, args);
            case "ahreload" -> List.of();
            case "agree", "disagree" -> List.of();
            default -> List.of();
        };
    }

    // ── /ahsell ──────────────────────────────────────────────────────────
    private List<String> completeAhsell(Player player, String[] args) {
        List<String> current = argsToList(args);
        int idx = args.length - 1; // индекс текущего аргумента
        String typing = args[idx].toLowerCase();

        // args[0] = цена
        if (idx == 0) {
            return filterStart(List.of("100", "500", "1000", "5000", "10000"), typing);
        }

        // Собираем уже введённые флаги
        boolean hasHand  = current.contains("hand");
        boolean hasAll   = current.contains("all") && !hasHand;
        boolean hasDays  = current.contains("--days");
        boolean hasAuto  = current.contains("--auto");
        boolean hasTop   = current.contains("--top");

        // Если предыдущий аргумент — --days или --top, предлагаем числа
        String prev = args.length >= 2 ? args[args.length - 2].toLowerCase() : "";
        if (prev.equals("--days") || prev.equals("--top")) {
            return filterStart(List.of("1", "2", "3", "5", "7", "14", "30"), typing);
        }

        // Если предыдущий аргумент — hand, предлагаем all или числа
        if (prev.equals("hand")) {
            return filterStart(List.of("all", "1", "8", "16", "32", "64"), typing);
        }

        // Составляем список доступных вариантов
        List<String> suggestions = new ArrayList<>();

        if (!hasHand && !hasAll) {
            suggestions.add("hand");
            suggestions.add("all");
        }
        if (!hasDays)  suggestions.add("--days");
        if (!hasAuto)  suggestions.add("--auto");
        if (!hasTop)   suggestions.add("--top");

        return filterStart(suggestions, typing);
    }

    // ── /ahcancel ─────────────────────────────────────────────────────────
    private List<String> completeAhcancel(Player player, String[] args) {
        if (args.length == 1) {
            String typing = args[0].toLowerCase();
            // Предлагаем ID лотов этого игрока
            return plugin.getAuctionManager()
                    .getListingsBySeller(player.getUniqueId())
                    .stream()
                    .map(AuctionListing::getId)
                    .filter(id -> id.toLowerCase().startsWith(typing))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // ── Утилиты ───────────────────────────────────────────────────────────
    private List<String> filterStart(List<String> list, String prefix) {
        if (prefix.isEmpty()) return new ArrayList<>(list);
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }

    private List<String> argsToList(String[] args) {
        List<String> list = new ArrayList<>();
        for (String a : args) list.add(a.toLowerCase());
        return list;
    }
}
