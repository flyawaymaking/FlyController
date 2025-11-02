package com.flyaway.flycontroller;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DataManager {
    private final FlyPlugin plugin;
    private File dataFolder;

    public DataManager(FlyPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public FlightData loadPlayerData(UUID playerId) {
        File playerFile = getPlayerFile(playerId);
        FlightData data = new FlightData();

        if (!playerFile.exists()) {
            return data; // Возвращаем пустые данные
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        data.setBalance(config.getDouble("balance", 0));
        data.setMaxUnlockedLevel(config.getInt("maxUnlockedLevel", 0));
        data.setCooldownEnd(config.getLong("cooldownEnd", 0));
        data.setFlightActive(config.getBoolean("flightActive", false));
        data.setFlightEndTime(config.getLong("flightEndTime", 0));
        data.setPausedTime(config.getLong("pausedTime", 0));

        return data;
    }

    public void savePlayerData(UUID playerId, FlightData data) {
        File playerFile = getPlayerFile(playerId);
        FileConfiguration config = new YamlConfiguration();

        config.set("balance", data.getBalance());
        config.set("maxUnlockedLevel", data.getMaxUnlockedLevel());
        config.set("cooldownEnd", data.getCooldownEnd());
        config.set("flightActive", data.isFlightActive());
        config.set("flightEndTime", data.getFlightEndTime());
        config.set("pausedTime", data.getPausedTime());

        try {
            config.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить данные игрока: " + playerId);
            e.printStackTrace();
        }
    }

    private File getPlayerFile(UUID playerId) {
        return new File(dataFolder, playerId.toString() + ".yml");
    }
}
