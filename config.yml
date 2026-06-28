package dev.auction.auctionhouse.gui;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AuctionGUI {

    private static final int ROWS = 6;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int INV_SIZE = ROWS * 9;

    private static final int SLOT_PREV     = 45;
    private static final int SLOT_INFO     = 49;
    private static final int SLOT_NEXT     = 53;
    private static final int SLOT_UNCLAIMED = 47;
    private static final int SLOT_MYLIST   = 51;

    private final AuctionHouse plugin;

    public AuctionGUI(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<AuctionListing> listings = plugin.getAuctionManager().getAllListings();
        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, totalPages));

        String title = ChatColor.GOLD + "" + ChatColor.BOLD + "Auction House " +
                ChatColor.GRAY + "(Page " + page + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < listings.size(); i++) {
            AuctionListing listing = listings.get(startIndex + i);
            inv.setItem(i, buildListingItem(listing));
        }

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, buildGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, buildGlass(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        if (page > 1) {
            inv.setItem(SLOT_PREV, buildNavItem(Material.ARROW,
                    ChatColor.YELLOW + "◀ Предыдущая страница",
                    ChatColor.GRAY + "Страница " + (page - 1)));
        }
        if (page < totalPages) {
            inv.setItem(SLOT_NEXT, buildNavItem(Material.ARROW,
                    ChatColor.YELLOW + "Следующая страница ▶",
                    ChatColor.GRAY + "Страница " + (page + 1)));
        }

        inv.setItem(SLOT_INFO, buildNavItem(Material.GOLD_INGOT,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Аукционный дом",
                ChatColor.GRAY + "Всего лотов: " + ChatColor.WHITE + listings.size(),
                ChatColor.GRAY + "Ваши лоты:   " + ChatColor.WHITE +
                        plugin.getAuctionManager().countListingsBySeller(player.getUniqueId()),
                ChatColor.GRAY + "Налог:       " + ChatColor.WHITE +
                        plugin.getConfig().getInt("tax-percent") + "%",
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Команды:",
                ChatColor.WHITE + "/ah " + ChatColor.GRAY + "— открыть аукцион",
                ChatColor.WHITE + "/ahsell <цена> " + ChatColor.GRAY + "— продать предмет в руке",
                ChatColor.WHITE + "/ahsell <цена> hand <N> " + ChatColor.GRAY + "— продать N шт.",
                ChatColor.WHITE + "/ahsell <цена> hand all " + ChatColor.GRAY + "— все предметы типа",
                ChatColor.WHITE + "/ahsell <цена> all " + ChatColor.GRAY + "— весь инвентарь",
                ChatColor.WHITE + "/ahcancel <id> " + ChatColor.GRAY + "— снять свой лот",
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Платные опции:",
                ChatColor.AQUA + "--days <N> " + ChatColor.GRAY + "— кастомная длит. (10к/сут)",
                ChatColor.LIGHT_PURPLE + "--auto " + ChatColor.GRAY + "— авто-продление (100к/мес)",
                ChatColor.GOLD + "--top <N> " + ChatColor.GRAY + "— топ позиция (10к/сут)",
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Примеры:",
                ChatColor.GREEN + "/ahsell 500 hand all",
                ChatColor.GREEN + "/ahsell 1000 all --days 7",
                ChatColor.GREEN + "/ahsell 500 --auto --top 3",
                ChatColor.GREEN + "/ahsell 200 hand 32 --days 3 --top 1"));

        inv.setItem(SLOT_MYLIST, buildNavItem(Material.CHEST,
                ChatColor.AQUA + "Мои лоты",
                ChatColor.GRAY + "Посмотреть ваши активные лоты"));

        boolean hasUnclaimed = plugin.getAuctionManager().hasUnclaimed(player.getUniqueId());
        inv.setItem(SLOT_UNCLAIMED, buildNavItem(
                hasUnclaimed ? Material.HOPPER : Material.BARREL,
                hasUnclaimed ? ChatColor.GREEN + "⚡ Есть непринятые предметы!" : ChatColor.GRAY + "Непринятые предметы",
                hasUnclaimed ? ChatColor.GREEN + "Нажмите, чтобы забрать!" : ChatColor.GRAY + "Пусто."));

        player.openInventory(inv);
    }

    public void openMyListings(Player player) {
        List<AuctionListing> myListings = plugin.getAuctionManager()
                .getListingsBySeller(player.getUniqueId());

        String title = ChatColor.AQUA + "" + ChatColor.BOLD + "My Listings";
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        for (int i = 0; i < Math.min(myListings.size(), ITEMS_PER_PAGE); i++) {
            inv.setItem(i, buildMyListingItem(myListings.get(i)));
        }

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, buildGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, buildGlass(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(SLOT_INFO, buildNavItem(Material.ARROW,
                ChatColor.YELLOW + "◀ Назад к аукциону",
                ChatColor.GRAY + "Вернуться к списку лотов"));

        player.openInventory(inv);
    }

    public void openUnclaimed(Player player) {
        List<ItemStack> items = plugin.getAuctionManager().getUnclaimed(player.getUniqueId());

        String title = ChatColor.GREEN + "" + ChatColor.BOLD + "Unclaimed Items";
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, title);

        for (int i = 0; i < Math.min(items.size(), ITEMS_PER_PAGE); i++) {
            ItemStack display = items.get(i).clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GREEN + "➤ Нажмите, чтобы забрать!");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(i, display);
        }

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, buildGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, buildGlass(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(SLOT_INFO, buildNavItem(Material.ARROW,
                ChatColor.YELLOW + "◀ Назад к аукциону",
                ChatColor.GRAY + "Вернуться к списку лотов"));

        player.openInventory(inv);
    }

    // ─────────────────────────── ITEM BUILDERS ────────────────────────────

    private ItemStack buildListingItem(AuctionListing listing) {
        ItemStack item = listing.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = meta.hasDisplayName()
                ? meta.getDisplayName()
                : ChatColor.WHITE + plugin.getAuctionManager().getItemName(item);

        // Для топ-лотов добавляем префикс
        if (listing.isTopSlot()) {
            displayName = ChatColor.GOLD + "★ " + ChatColor.RESET + displayName;
        }

        List<String> lore = new ArrayList<>();

        if (listing.isTopSlot()) {
            lore.add(ChatColor.GOLD + "✦ ТОП ЛОТ ✦");
        }
        if (listing.isAutoRenew()) {
            lore.add(ChatColor.LIGHT_PURPLE + "♻ Авто-продление");
        }

        lore.add(ChatColor.GRAY + "Продавец: " + ChatColor.YELLOW + listing.getSellerName());
        lore.add(ChatColor.GRAY + "Кол-во:   " + ChatColor.WHITE + listing.getItem().getAmount() + " шт.");
        lore.add(ChatColor.GRAY + "Цена:     " + ChatColor.GREEN +
                plugin.getConfig().getString("currency-symbol", "$") +
                String.format("%,.2f", listing.getPrice()));
        lore.add(ChatColor.GRAY + "Истекает: " + ChatColor.WHITE + listing.getFormattedTimeLeft());
        lore.add(ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY + listing.getId());
        lore.add("");
        lore.add(ChatColor.YELLOW + "➤ ЛКМ — купить");

        if (meta.hasLore()) {
            lore.add(ChatColor.DARK_GRAY + "──── Описание предмета ────");
            lore.addAll(meta.getLore());
        }

        meta.setDisplayName(displayName);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildMyListingItem(AuctionListing listing) {
        ItemStack item = listing.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = meta.hasDisplayName()
                ? meta.getDisplayName()
                : ChatColor.WHITE + plugin.getAuctionManager().getItemName(item);

        if (listing.isTopSlot()) {
            displayName = ChatColor.GOLD + "★ " + ChatColor.RESET + displayName;
        }

        List<String> lore = new ArrayList<>();
        if (listing.isTopSlot()) lore.add(ChatColor.GOLD + "✦ ТОП ЛОТ ✦");
        if (listing.isAutoRenew()) lore.add(ChatColor.LIGHT_PURPLE + "♻ Авто-продление активно");

        lore.add(ChatColor.GRAY + "Кол-во:   " + ChatColor.WHITE + listing.getItem().getAmount() + " шт.");
        lore.add(ChatColor.GRAY + "Цена:     " + ChatColor.GREEN +
                plugin.getConfig().getString("currency-symbol", "$") +
                String.format("%,.2f", listing.getPrice()));
        lore.add(ChatColor.GRAY + "Истекает: " + ChatColor.WHITE + listing.getFormattedTimeLeft());
        lore.add(ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY + listing.getId());
        lore.add("");
        lore.add(ChatColor.RED + "➤ ПКМ — отменить и вернуть");

        meta.setDisplayName(displayName);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildGlass(Material mat, String name) {
        ItemStack glass = new ItemStack(mat);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(name);
        glass.setItemMeta(meta);
        return glass;
    }

    private ItemStack buildNavItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(List.of(loreLines)));
        meta.addItemFlags(
            org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
            org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
            org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP
        );
        item.setItemMeta(meta);
        return item;
    }
}
