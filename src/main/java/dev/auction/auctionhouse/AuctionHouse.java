package dev.auction.auctionhouse;

import dev.auction.auctionhouse.commands.*;
import dev.auction.auctionhouse.gui.GUIListener;
import dev.auction.auctionhouse.manager.AuctionManager;
import dev.auction.auctionhouse.manager.EconomyManager;
import dev.auction.auctionhouse.model.PendingSell;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionHouse extends JavaPlugin {

    private static AuctionHouse instance;
    private AuctionManager auctionManager;
    private EconomyManager economyManager;

    /** Ожидающие подтверждения продажи всего инвентаря (/agree) */
    private final Map<UUID, PendingSell> pendingSells = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        economyManager = new EconomyManager(this);
        if (!economyManager.setup()) {
            getLogger().severe("Vault + Economy plugin not found! Disabling AuctionHouse.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        auctionManager = new AuctionManager(this);
        auctionManager.load();

        getCommand("ah").setExecutor(new AHCommand(this));
        getCommand("ahsell").setExecutor(new AHSellCommand(this));
        getCommand("ahcancel").setExecutor(new AHCancelCommand(this));
        getCommand("ahreload").setExecutor(new AHReloadCommand(this));
        getCommand("agree").setExecutor(new AgreeCommand(this));
        getCommand("disagree").setExecutor(new DisagreeCommand(this));

        // Tab completers
        AHTabCompleter tab = new AHTabCompleter(this);
        getCommand("ahsell").setTabCompleter(tab);
        getCommand("ahcancel").setTabCompleter(tab);

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        getServer().getScheduler().runTaskTimer(this, auctionManager::checkExpired, 1200L, 1200L);

        getLogger().info("AuctionHouse enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) auctionManager.save();
        getLogger().info("AuctionHouse disabled. Data saved.");
    }

    public static AuctionHouse getInstance() { return instance; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public Map<UUID, PendingSell> getPendingSells() { return pendingSells; }

    public String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "[AH] ");
        String msg = getConfig().getString("messages." + key, "§cMessage not found: " + key);
        return prefix + msg;
    }

    public String msgRaw(String key) {
        return getConfig().getString("messages." + key, "§cMessage not found: " + key);
    }
}
