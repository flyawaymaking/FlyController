package com.flyaway.flycontroller;

import com.flyaway.flycontroller.managers.*;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class FlyPlugin extends JavaPlugin implements Listener {
    private EconomyManager economyManager;
    private DataManager dataManager;
    private ConfigManager configManager;
    private PlayerManager playerManager;
    private FlightManager flightManager;

    @Override
    public void onEnable() {
        this.dataManager = new DataManager(this);
        this.configManager = new ConfigManager(this);
        configManager.loadConfig();
        this.playerManager = new PlayerManager(this);
        this.economyManager = new EconomyManager(this);
        this.flightManager = new FlightManager(this);

        playerManager.registerListeners();
        playerManager.registerCommands();
        flightManager.startTasks();

        List<String> worlds = configManager.getAllowedWorlds();
        String loadedWorlds = worlds.isEmpty() ? "Все" : String.join(", ", worlds);
        getLogger().info("Разрешённые миры: " + loadedWorlds);
        getLogger().info("Валюта: " + economyManager.getCurrencyName());
        getLogger().info("Плагин успешно запущен!");
    }

    public void reloadConfiguration() {
        configManager.reloadConfig();
        economyManager.reload();
        getLogger().info("Плагин перезагружен!");

    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public void onDisable() {
        flightManager.disable();
        getLogger().info("Плагин выключен!");
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public FlightManager getFlightManager() {
        return flightManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
