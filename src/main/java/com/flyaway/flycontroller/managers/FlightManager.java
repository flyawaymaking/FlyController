package com.flyaway.flycontroller.managers;

import com.flyaway.flycontroller.models.FlightData;
import com.flyaway.flycontroller.models.FlightTier;
import com.flyaway.flycontroller.FlyPlugin;
import com.flyaway.flycontroller.utils.TimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlightManager {
    private final FlyPlugin plugin;
    private final ConfigManager configManager;
    private final EconomyManager economyManager;
    private final DataManager dataManager;
    private final PlayerManager playerManager;
    private final ActionBarManager actionBarManager;

    private final Map<UUID, Long> activeFlightTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pausedFlightTimes = new ConcurrentHashMap<>();
    private BukkitTask flightTimerTask;
    private BukkitTask actionBarTimerTask;

    public FlightManager(FlyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.economyManager = plugin.getEconomyManager();
        this.dataManager = plugin.getDataManager();
        this.playerManager = plugin.getPlayerManager();
        this.actionBarManager = new ActionBarManager(plugin);
    }

    public void startTasks() {
        startFlightTimer();
        startActionBarTimer();
    }

    public void disable() {
        if (flightTimerTask != null) {
            flightTimerTask.cancel();
            flightTimerTask = null;
        }
        if (actionBarTimerTask != null) {
            actionBarTimerTask.cancel();
            actionBarTimerTask = null;
        }

        // Сохраняем активные полёты при выключении
        for (Map.Entry<UUID, Long> entry : activeFlightTimes.entrySet()) {
            UUID playerId = entry.getKey();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                pauseActiveFlight(player, configManager.getMessage("disable-reason.plugin-disable"));
            }
        }
    }

    public boolean activateFlight(Player player) {
        UUID playerId = player.getUniqueId();
        FlightData data = dataManager.loadPlayerData(playerId);

        if (data.getBalance() == 0) {
            playerManager.sendMessage(player, configManager.getMessage("no-balance"));
            return false;
        }

        int maxLevel = calculateFlightLevel(data.getBalance());
        if (maxLevel == 0) {
            playerManager.sendMessage(player, configManager.getMessage("insufficient-funds"));
            return false;
        }

        if (data.getCooldownEnd() > System.currentTimeMillis()) {
            long remaining = data.getCooldownEnd() - System.currentTimeMillis();
            Map<String, String> placeholders = Map.of("time", TimeFormatter.formatTime(remaining, configManager));
            playerManager.sendMessage(player, configManager.getMessage("cooldown-active", placeholders));
            return false;
        }

        if (!configManager.isWorldAllowed(player.getWorld())) {
            playerManager.sendMessage(player, configManager.getMessage("activate-not-allowed-world"));
            return false;
        }

        FlightTier tier = configManager.getFlightTiers().get(maxLevel);
        long endTime = System.currentTimeMillis() + (tier.getDuration() * 1000L);
        activeFlightTimes.put(playerId, endTime);

        data.setFlightActive(true);
        data.setFlightEndTime(endTime);
        data.setCooldownEnd(endTime + configManager.getCooldownTime());

        dataManager.savePlayerData(playerId, data);

        enableFlight(player);

        Map<String, String> placeholders = Map.of(
                "level", String.valueOf(maxLevel),
                "minutes", String.valueOf(tier.getDuration() / 60)
        );
        playerManager.sendMessage(player, configManager.getMessage("flight-activated", placeholders));

        return true;
    }

    public void pauseActiveFlight(Player player, String reason) {
        UUID playerId = player.getUniqueId();

        Long remainingTime = getRemainingFlightTime(player);
        if (remainingTime > 0) {
            FlightData data = dataManager.loadPlayerData(playerId);
            data.setPausedTime(remainingTime);
            data.setFlightActive(false);
            data.setFlightEndTime(0);

            pausedFlightTimes.put(playerId, remainingTime);

            dataManager.savePlayerData(playerId, data);

            Map<String, String> placeholders = Map.of("reason", reason, "time", TimeFormatter.formatTime(remainingTime, configManager));

            playerManager.sendMessage(player, configManager.getMessage("flight-paused", placeholders));
        } else {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                playerManager.sendMessage(player, configManager.getMessage("flight-disabled", Map.of("reason", reason)));
            }
        }

        disableFlight(player);
        activeFlightTimes.remove(playerId);
    }

    public boolean depositMoney(Player player, double amount) {
        if (!economyManager.isEconomyAvailable()) {
            playerManager.sendMessage(player, configManager.getMessage("economy-unavailable"));
            return false;
        }

        UUID playerId = player.getUniqueId();
        FlightData data = dataManager.loadPlayerData(playerId);
        double currentBalance = data.getBalance();

        int currentLevel = calculateFlightLevel(currentBalance);
        int maxLevel = getMaxFlightLevel();

        if (currentLevel >= maxLevel) {
            playerManager.sendMessage(player, configManager.getMessage("max-level-reached",
                    Map.of("level", String.valueOf(maxLevel))));
            return false;
        }

        double newBalance = currentBalance + amount;
        int newLevel = calculateFlightLevel(newBalance);

        if (newLevel > maxLevel) {
            double maxAmountNeeded = configManager.getFlightTiers().get(maxLevel).getMinAmount() - currentBalance;
            if (maxAmountNeeded <= 0) {
                playerManager.sendMessage(player, configManager.getMessage("max-level-reached",
                        Map.of("level", String.valueOf(maxLevel))));
                return false;
            }

            Map<String, String> placeholders = Map.of(
                    "amount", String.valueOf(maxAmountNeeded),
                    "currency", economyManager.getCurrencySymbol()
            );
            playerManager.sendMessage(player, configManager.getMessage("deposit-max-amount-suggestion", placeholders));
            return false;
        }

        if (!economyManager.hasEnoughMoney(player, amount)) {
            return false;
        }

        if (!economyManager.withdrawMoney(player, amount)) {
            return false;
        }

        data.setBalance(newBalance);

        dataManager.savePlayerData(playerId, data);

        if (newLevel > data.getMaxUnlockedLevel()) {
            data.setMaxUnlockedLevel(newLevel);
            playerManager.sendMessage(player, configManager.getMessage("level-up",
                    Map.of("level", String.valueOf(newLevel))));
        }

        String currencySymbol = economyManager.getCurrencySymbol();
        Map<String, String> placeholders = Map.of(
                "amount", String.valueOf(amount),
                "currency", currencySymbol,
                "balance", String.valueOf(newBalance),
                "level", String.valueOf(newLevel)
        );
        playerManager.sendMessage(player, configManager.getMessage("deposit-success", placeholders));

        return true;
    }

    public boolean activatePausedFlight(Player player) {
        UUID playerId = player.getUniqueId();

        FlightData data = dataManager.loadPlayerData(playerId);
        if (data == null) {
            return false;
        }

        Long pausedTime = pausedFlightTimes.get(playerId);
        if (pausedTime == null || pausedTime <= 0) {
            pausedTime = data.getPausedTime();
            if (pausedTime <= 0) {
                return false;
            }
            pausedFlightTimes.put(playerId, pausedTime);
        }

        if (activeFlightTimes.containsKey(playerId)) {
            return false;
        }

        long endTime = System.currentTimeMillis() + pausedTime;
        activeFlightTimes.put(playerId, endTime);

        data.setFlightActive(true);
        data.setFlightEndTime(endTime);
        data.setPausedTime(0);

        dataManager.savePlayerData(playerId, data);

        pausedFlightTimes.remove(playerId);

        enableFlight(player);

        plugin.getLogger().info("Активирован полёт из паузы для " + player.getName() + ", время: " + (pausedTime / 1000) + " сек");

        return true;
    }

    public boolean continueFlight(Player player) {
        UUID playerId = player.getUniqueId();

        if (!configManager.isWorldAllowed(player.getWorld())) {
            playerManager.sendMessage(player, configManager.getMessage("continue-not-allowed-world"));
            return false;
        }

        Long pausedTimeBeforeActivation = pausedFlightTimes.get(playerId);
        if (pausedTimeBeforeActivation == null) {
            FlightData data = dataManager.loadPlayerData(playerId);
            pausedTimeBeforeActivation = data.getPausedTime();
        }

        if (activatePausedFlight(player)) {
            if (pausedTimeBeforeActivation > 0) {
                Map<String, String> placeholders = Map.of("time", TimeFormatter.formatTime(pausedTimeBeforeActivation, configManager));
                playerManager.sendMessage(player, configManager.getMessage("flight-continued-time", placeholders));
            } else {
                playerManager.sendMessage(player, configManager.getMessage("flight-continued"));
            }
            return true;
        } else {
            playerManager.sendMessage(player, configManager.getMessage("no-saved-flight-time"));
            return false;
        }
    }

    public void handleCombat(Player player) {
        if (player.getAllowFlight() || activeFlightTimes.containsKey(player.getUniqueId())) {
            pauseActiveFlight(player, configManager.getMessage("disable-reason.combat"));
        }
    }

    private void startFlightTimer() {
        this.flightTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                Iterator<Map.Entry<UUID, Long>> activeIterator = activeFlightTimes.entrySet().iterator();
                while (activeIterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = activeIterator.next();
                    UUID playerId = entry.getKey();
                    Long endTime = entry.getValue();

                    if (currentTime >= endTime) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            disableFlight(player);
                            playerManager.sendMessage(player, configManager.getMessage("flight-time-expired"));
                        }
                        activeIterator.remove();

                        FlightData data = dataManager.loadPlayerData(playerId);
                        if (data != null) {
                            data.setFlightActive(false);
                            data.setFlightEndTime(0);
                            dataManager.savePlayerData(playerId, data);
                        }
                    }
                }

                Iterator<Map.Entry<UUID, Long>> pausedIterator = pausedFlightTimes.entrySet().iterator();
                while (pausedIterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = pausedIterator.next();
                    UUID playerId = entry.getKey();
                    if (entry.getValue() <= 0) {
                        pausedIterator.remove();

                        FlightData data = dataManager.loadPlayerData(playerId);
                        if (data != null) {
                            data.setPausedTime(0);
                            dataManager.savePlayerData(playerId, data);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startActionBarTimer() {
        this.actionBarTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Long> entry : activeFlightTimes.entrySet()) {
                    UUID playerId = entry.getKey();
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        actionBarManager.sendFlightTime(player, entry.getValue());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void enableFlight(Player player) {
        // Включаем полёт с задержкой, чтобы Essentials успел завершить свою логику
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }

            if (!player.isFlying()) {
                player.setFlying(true);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeFlightTimes.containsKey(player.getUniqueId()) && (!player.getAllowFlight() || !player.isFlying())) {
                    player.setAllowFlight(true);
                    player.setFlying(true);
                }
            }, 10L);
        }, 20L);
    }

    public void disableFlight(Player player) {
        if (!isInCreativeOrSpectator(player)) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    private boolean isInCreativeOrSpectator(Player player) {
        return player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                player.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }

    public Long getRemainingFlightTime(Player player) {
        UUID playerId = player.getUniqueId();
        if (activeFlightTimes.containsKey(playerId)) {
            long endTime = activeFlightTimes.get(playerId);
            long remaining = endTime - System.currentTimeMillis();
            return remaining > 0 ? remaining : 0;
        }
        return 0L;
    }

    public int calculateFlightLevel(double balance) {
        int highestLevel = 0;
        for (FlightTier tier : configManager.getFlightTiers().values()) {
            if (balance >= tier.getMinAmount()) {
                highestLevel = Math.max(highestLevel, tier.getLevel());
            }
        }
        return highestLevel;
    }

    public int getMaxFlightLevel() {
        return configManager.getFlightTiers().keySet().stream()
                .max(Integer::compareTo)
                .orElse(0);
    }

    public boolean hasActiveFlightTimes(UUID playerId) {
        return activeFlightTimes.containsKey(playerId);
    }

    public long getPausedFlightTime(UUID playerId) {
        return pausedFlightTimes.get(playerId);
    }

    public Map<UUID, Long> getPausedFlightTimes() {
        return pausedFlightTimes;
    }

    public Long setPausedFlightTime(UUID playerId, long pausedTime) {
        return pausedFlightTimes.put(playerId, pausedTime);
    }
}
