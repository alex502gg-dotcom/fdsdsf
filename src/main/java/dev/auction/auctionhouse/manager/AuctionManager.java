package dev.auction.auctionhouse.manager;

import dev.auction.auctionhouse.AuctionHouse;
import dev.auction.auctionhouse.model.AuctionListing;
import dev.auction.auctionhouse.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AuctionManager {

    private final AuctionHouse plugin;
    private final Map<String, AuctionListing> listings = new ConcurrentHashMap<>();
    // UUID -> list of items that couldn't fit in inventory (expired/sold)
    private final Map<UUID, List<ItemStack>> unclaimed = new ConcurrentHashMap<>();

    private File dataFile;
    private File unclaimedFile;

    public AuctionManager(AuctionHouse plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "auctions.yml");
        this.unclaimedFile = new File(plugin.getDataFolder(), "unclaimed.yml");
    }

    // ───────────────────────────── PUBLIC API ─────────────────────────────

    public boolean addListing(AuctionListing listing) {
        listings.put(listing.getId(), listing);
        return true;
    }

    public boolean removeListing(String id) {
        return listings.remove(id) != null;
    }

    public Optional<AuctionListing> getListing(String id) {
        return Optional.ofNullable(listings.get(id));
    }

    public List<AuctionListing> getAllListings() {
        return listings.values().stream()
                .filter(l -> !l.isExpired())
                .collect(Collectors.toList());
    }

    public List<AuctionListing> getListingsBySeller(UUID uuid) {
        return listings.values().stream()
                .filter(l -> l.getSellerUUID().equals(uuid) && !l.isExpired())
                .collect(Collectors.toList());
    }

    public int countListingsBySeller(UUID uuid) {
        return (int) listings.values().stream()
                .filter(l -> l.getSellerUUID().equals(uuid) && !l.isExpired())
                .count();
    }

    public String generateId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ───────────────────────────── UNCLAIMED ──────────────────────────────

    public void addUnclaimed(UUID uuid, ItemStack item) {
        unclaimed.computeIfAbsent(uuid, k -> new ArrayList<>()).add(item.clone());
    }

    public List<ItemStack> getUnclaimed(UUID uuid) {
        return unclaimed.getOrDefault(uuid, Collections.emptyList());
    }

    public void clearUnclaimed(UUID uuid) {
        unclaimed.remove(uuid);
    }

    public boolean hasUnclaimed(UUID uuid) {
        List<ItemStack> items = unclaimed.get(uuid);
        return items != null && !items.isEmpty();
    }

    // ───────────────────────────── EXPIRY ─────────────────────────────────

    public void checkExpired() {
        List<AuctionListing> expired = listings.values().stream()
                .filter(AuctionListing::isExpired)
                .collect(Collectors.toList());

        for (AuctionListing listing : expired) {
            listings.remove(listing.getId());
            returnItem(listing.getSellerUUID(), listing.getItem(),
                    plugin.msgRaw("auction-expired")
                            .replace("{item}", getItemName(listing.getItem())));
        }

        if (!expired.isEmpty()) {
            save();
        }
    }

    private void returnItem(UUID uuid, ItemStack item, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            if (hasSpaceFor(player, item)) {
                player.getInventory().addItem(item);
                player.sendMessage(plugin.msg("auction-expired")
                        .replace("{item}", getItemName(item)));
            } else {
                addUnclaimed(uuid, item);
                player.sendMessage(plugin.msg("inventory-full"));
            }
        } else {
            addUnclaimed(uuid, item);
        }
    }

    private boolean hasSpaceFor(Player player, ItemStack item) {
        return Arrays.stream(player.getInventory().getStorageContents())
                .anyMatch(slot -> slot == null || slot.getType().isAir());
    }

    public String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        // Convert material name to readable format
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ───────────────────────────── PERSISTENCE ────────────────────────────

    public void load() {
        loadAuctions();
        loadUnclaimed();
    }

    public void save() {
        saveAuctions();
        saveUnclaimed();
    }

    private void loadAuctions() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

        if (!cfg.contains("auctions")) return;

        for (String id : cfg.getConfigurationSection("auctions").getKeys(false)) {
            try {
                String path = "auctions." + id + ".";
                UUID sellerUUID = UUID.fromString(cfg.getString(path + "seller-uuid"));
                String sellerName = cfg.getString(path + "seller-name");
                double price = cfg.getDouble(path + "price");
                long expiresAt = cfg.getLong(path + "expires-at");
                String itemData = cfg.getString(path + "item");
                ItemStack item = ItemSerializer.fromBase64(itemData);

                if (item == null) continue;

                AuctionListing listing = new AuctionListing(id, sellerUUID, sellerName,
                        item, price, expiresAt, true);

                if (!listing.isExpired()) {
                    listings.put(id, listing);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load auction " + id + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + listings.size() + " auctions.");
    }

    private void saveAuctions() {
        FileConfiguration cfg = new YamlConfiguration();

        for (AuctionListing listing : listings.values()) {
            if (listing.isExpired()) continue;
            String path = "auctions." + listing.getId() + ".";
            cfg.set(path + "seller-uuid", listing.getSellerUUID().toString());
            cfg.set(path + "seller-name", listing.getSellerName());
            cfg.set(path + "price", listing.getPrice());
            cfg.set(path + "expires-at", listing.getExpiresAt());
            try {
                cfg.set(path + "item", ItemSerializer.toBase64(listing.getItem()));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to serialize item for auction " + listing.getId());
            }
        }

        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save auctions.yml: " + e.getMessage());
        }
    }

    private void loadUnclaimed() {
        if (!unclaimedFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(unclaimedFile);

        if (!cfg.contains("unclaimed")) return;

        for (String uuidStr : cfg.getConfigurationSection("unclaimed").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> itemDataList = cfg.getStringList("unclaimed." + uuidStr + ".items");
                List<ItemStack> items = new ArrayList<>();
                for (String data : itemDataList) {
                    ItemStack item = ItemSerializer.fromBase64(data);
                    if (item != null) items.add(item);
                }
                if (!items.isEmpty()) unclaimed.put(uuid, items);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load unclaimed for " + uuidStr);
            }
        }
    }

    private void saveUnclaimed() {
        FileConfiguration cfg = new YamlConfiguration();

        for (Map.Entry<UUID, List<ItemStack>> entry : unclaimed.entrySet()) {
            List<String> serialized = new ArrayList<>();
            for (ItemStack item : entry.getValue()) {
                try {
                    serialized.add(ItemSerializer.toBase64(item));
                } catch (Exception ignored) {}
            }
            cfg.set("unclaimed." + entry.getKey() + ".items", serialized);
        }

        try {
            cfg.save(unclaimedFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save unclaimed.yml: " + e.getMessage());
        }
    }
}
