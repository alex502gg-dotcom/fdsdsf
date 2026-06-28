package dev.auction.auctionhouse;

import dev.auction.auctionhouse.commands.AHCommand;
import dev.auction.auctionhouse.commands.AHSellCommand;
import dev.auction.auctionhouse.commands.AHCancelCommand;
import dev.auction.auctionhouse.commands.AHReloadCommand;
import dev.auction.auctionhouse.gui.GUIListener;
import dev.auction.auctionhouse.manager.AuctionManager;
import dev.auction.auctionhouse.manager.EconomyManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionHouse extends JavaPlugin {

    private static AuctionHouse instance;
    private AuctionManager auctionManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Setup economy
        economyManager = new EconomyManager(this);
        if (!economyManager.setup()) {
            getLogger().severe("Vault + Economy plugin not found! Disabling AuctionHouse.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Setup auction manager (loads data)
        auctionManager = new AuctionManager(this);
        auctionManager.load();

        // Register commands
        getCommand("ah").setExecutor(new AHCommand(this));
        getCommand("ahsell").setExecutor(new AHSellCommand(this));
        getCommand("ahcancel").setExecutor(new AHCancelCommand(this));
        getCommand("ahreload").setExecutor(new AHReloadCommand(this));

        // Register GUI listener
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        // Start expiry task (check every 60 seconds)
        getServer().getScheduler().runTaskTimer(this, auctionManager::checkExpired, 1200L, 1200L);

        getLogger().info("AuctionHouse enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) {
            auctionManager.save();
        }
        getLogger().info("AuctionHouse disabled. Data saved.");
    }

    public static AuctionHouse getInstance() {
        return instance;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "[AH] ");
        String msg = getConfig().getString("messages." + key, "§cMessage not found: " + key);
        return prefix + msg;
    }

    public String msgRaw(String key) {
        return getConfig().getString("messages." + key, "§cMessage not found: " + key);
    }
}
