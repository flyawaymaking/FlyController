package com.flyaway.flycontroller;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class ConfigManager {
    private final FlyPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(FlyPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public Map<Integer, FlightTier> getFlightTiers() {
        Map<Integer, FlightTier> tiers = new HashMap<>();

        if (config.contains("flight-tiers")) {
            for (String key : config.getConfigurationSection("flight-tiers").getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    double cost = config.getDouble("flight-tiers." + key + ".cost");
                    int duration = config.getInt("flight-tiers." + key + ".duration");

                    tiers.put(level, new FlightTier(level, cost, duration));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Неверный формат уровня полёта: " + key);
                }
            }
        }

        // Если в конфиге нет уровней, используем значения по умолчанию
        if (tiers.isEmpty()) {
            tiers.put(1, new FlightTier(1, 50000, 120));
            tiers.put(2, new FlightTier(2, 100000, 300));
            tiers.put(3, new FlightTier(3, 200000, 600));
            plugin.getLogger().warning("Используются уровни полёта по умолчанию");
        }

        return tiers;
    }

    public Map<Integer, Float> getFlySpeeds() {
        Map<Integer, Float> speeds = new HashMap<>();

        if (config.contains("fly-speeds")) {
            for (String key : config.getConfigurationSection("fly-speeds").getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    float speed = (float) config.getDouble("fly-speeds." + key);
                    speeds.put(level, speed);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Неверный формат скорости полёта: " + key);
                }
            }
        }

        // Значения по умолчанию
        if (speeds.isEmpty()) {
            speeds.put(1, 0.1f);
            speeds.put(2, 0.2f);
            speeds.put(3, 0.4f);
        }

        return speeds;
    }

    public List<String> getWorlds() {
        return config.getStringList("worlds");
    }

    public String getCurrency() {
        return config.getString("currency", "money"); // money по умолчанию
    }

    public long getCooldownTime() {
        return config.getLong("cooldown", 600000); // 10 минут по умолчанию
    }
}
