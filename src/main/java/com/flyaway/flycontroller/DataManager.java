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

    public void loadAllPlayerData() {
        // Загружаем данные только для онлайн игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerData(player.getUniqueId());
        }
    }

    public void loadPlayerData(UUID playerId) {
        File playerFile = getPlayerFile(playerId);
        if (!playerFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        FlightData data = new FlightData();

        data.setBalance(config.getDouble("balance", 0));
        data.setMaxUnlockedLevel(config.getInt("maxUnlockedLevel", 0));
        data.setCooldownEnd(config.getLong("cooldownEnd", 0));
        data.setFlightActive(config.getBoolean("flightActive", false));
        data.setFlightEndTime(config.getLong("flightEndTime", 0));
        data.setPausedTime(config.getLong("pausedTime", 0)); // ДОБАВЬТЕ

        plugin.setPlayerFlightData(playerId, data);

        // НЕ восстанавливаем активный полёт при входе - только сохранённое время
        // Активный полёт должен быть активирован через /mfly continue
    }

    public void saveAllPlayerData() {
        for (UUID playerId : plugin.getAllPlayerFlightData().keySet()) {
            savePlayerData(playerId);
        }
    }

    public void savePlayerData(UUID playerId) {
        FlightData data = plugin.getPlayerFlightData(playerId);
        if (data == null) {
            return;
        }

        File playerFile = getPlayerFile(playerId);
        FileConfiguration config = new YamlConfiguration();

        config.set("balance", data.getBalance());
        config.set("maxUnlockedLevel", data.getMaxUnlockedLevel());
        config.set("cooldownEnd", data.getCooldownEnd());
        config.set("flightActive", data.isFlightActive());
        config.set("flightEndTime", data.getFlightEndTime());
        config.set("pausedTime", data.getPausedTime()); // ДОБАВЬТЕ

        try {
            config.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить данные игрока: " + playerId);
            e.printStackTrace();
        }
    }

    public void savePlayerData(Player player) {
        savePlayerData(player.getUniqueId());
    }

    private File getPlayerFile(UUID playerId) {
        return new File(dataFolder, playerId.toString() + ".yml");
    }
}
