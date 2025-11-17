package com.flyaway.flycontroller.managers;

import com.flyaway.flycontroller.models.FlightTier;
import com.flyaway.flycontroller.FlyPlugin;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class ConfigManager {
    private final FlyPlugin plugin;
    private FileConfiguration config;
    private Map<Integer, FlightTier> flightTiers;
    private Map<Integer, Float> flySpeeds;
    private List<String> allowedWorlds;

    public ConfigManager(FlyPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        setFlightTiers();
        setFlySpeeds();
        setAllowedWorlds();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
    }

    public void setFlightTiers() {
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

        this.flightTiers = tiers;
    }

    public void setFlySpeeds() {
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

        this.flySpeeds = speeds;
    }

    public void setAllowedWorlds() {
        this.allowedWorlds = config.getStringList("worlds");
    }

    public @NotNull String getPrefix() {
        return config.getString("prefix", "<gray>[<blue>FlyController</blue>]</gray>");
    }

    public @NotNull String getMessage(String key) {
        return getMessage(key, null);
    }

    public @NotNull String getMessage(String key, Map<String, String> placeholders) {
        String message = config.getString("messages." + key, "<red>message." + key + " not-found");
        return formatMessage(message, placeholders);
    }

    private @NotNull String formatMessage(String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty()) return "";
        message = message.replaceAll("\\s+$", "");

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return message;
    }

    public boolean isWorldAllowed(World world) {
        return allowedWorlds.isEmpty() || allowedWorlds.contains(world.getName());
    }

    public Map<Integer, FlightTier> getFlightTiers() {
        return flightTiers;
    }

    public Map<Integer, Float> getFlightSpeeds() {
        return flySpeeds;
    }

    public List<String> getAllowedWorlds() {
        return allowedWorlds;
    }

    public String getCurrency() {
        return config.getString("currency", "money"); // money по умолчанию
    }

    public long getCooldownTime() {
        return config.getLong("cooldown", 600000); // 10 минут по умолчанию
    }
}
