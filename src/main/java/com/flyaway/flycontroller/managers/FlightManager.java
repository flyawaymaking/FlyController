package com.flyaway.flycontroller.managers;

import com.flyaway.flycontroller.models.FlightData;
import com.flyaway.flycontroller.models.FlightTier;
import com.flyaway.flycontroller.FlyPlugin;
import com.flyaway.flycontroller.utils.TimeFormatter;
import org.bukkit.Bukkit;
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
        cancelTask(flightTimerTask);
        cancelTask(actionBarTimerTask);

        // Сохраняем активные полёты при выключении
        activeFlightTimes.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .forEach(player -> pauseActiveFlight(player, configManager.getMessage("disable-reason.plugin-disable")));
    }

    public boolean activateFlight(Player player) {
        if (haveActiveFlight(player)) return false;
        UUID playerId = player.getUniqueId();
        FlightData data = dataManager.loadPlayerData(playerId);

        if (!validateFlightActivation(player, data)) {
            return false;
        }

        int maxLevel = calculateFlightLevel(data.getBalance());
        FlightTier tier = configManager.getFlightTiers().get(maxLevel);
        long endTime = System.currentTimeMillis() + (tier.getDuration() * 1000L);

        setupActiveFlight(playerId, data, endTime);
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
            if (!isInCreativeOrSpectator(player)) {
                playerManager.sendMessage(player, configManager.getMessage("flight-disabled", Map.of("reason", reason)));
            }
        }

        disableFlight(player);
        activeFlightTimes.remove(playerId);
    }

    public boolean depositMoney(Player player, double amount) {
        if (!validateEconomy()) {
            playerManager.sendMessage(player, configManager.getMessage("economy-unavailable"));
            return false;
        }

        UUID playerId = player.getUniqueId();
        FlightData data = dataManager.loadPlayerData(playerId);
        double currentBalance = data.getBalance();

        if (!validateDeposit(player, data, currentBalance, amount)) {
            return false;
        }

        double newBalance = currentBalance + amount;
        data.setBalance(newBalance);
        dataManager.savePlayerData(playerId, data);

        handleLevelUp(player, data, newBalance);
        sendDepositSuccessMessage(player, amount, newBalance);

        return true;
    }

    public boolean activatePausedFlight(Player player) {
        if (haveActiveFlight(player)) return false;
        UUID playerId = player.getUniqueId();

        Long pausedTime = getValidPausedTime(playerId);
        if (pausedTime == null || pausedTime <= 0) {
            return false;
        }

        long endTime = System.currentTimeMillis() + pausedTime;
        activeFlightTimes.put(playerId, endTime);

        FlightData data = dataManager.loadPlayerData(playerId);
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

        Long pausedTimeBeforeActivation = getValidPausedTime(playerId);
        if (pausedTimeBeforeActivation == null) {
            playerManager.sendMessage(player, configManager.getMessage("no-saved-flight-time"));
            return false;
        }

        if (activatePausedFlight(player)) {
            Map<String, String> placeholders = pausedTimeBeforeActivation > 0 ?
                    Map.of("time", TimeFormatter.formatTime(pausedTimeBeforeActivation, configManager)) :
                    Map.of();

            String messageKey = pausedTimeBeforeActivation > 0 ? "flight-continued-time" : "flight-continued";
            playerManager.sendMessage(player, configManager.getMessage(messageKey, placeholders));
            return true;
        }

        return false;
    }

    public void handleCombat(Player player) {
        if (shouldManageFlight(player)) {
            pauseActiveFlight(player, configManager.getMessage("disable-reason.combat"));
        }
    }

    // Вспомогательные приватные методы
    private boolean validateFlightActivation(Player player, FlightData data) {
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

        return true;
    }

    private void setupActiveFlight(UUID playerId, FlightData data, long endTime) {
        activeFlightTimes.put(playerId, endTime);
        data.setFlightActive(true);
        data.setFlightEndTime(endTime);
        data.setCooldownEnd(endTime + configManager.getCooldownTime());
        dataManager.savePlayerData(playerId, data);
    }

    private boolean haveActiveFlight(Player player) {
        if (player.getAllowFlight()) {
            playerManager.sendMessage(player, configManager.getMessage("flight-active"));
            return true;
        }

        if (activeFlightTimes.containsKey(player.getUniqueId())) {
            enableFlight(player);
            playerManager.sendMessage(player, configManager.getMessage("flight-reactivate"));
            return true;
        }
        return false;
    }

    private boolean validateEconomy() {
        return economyManager.isEconomyAvailable();
    }

    private boolean validateDeposit(Player player, FlightData data, double currentBalance, double amount) {
        int currentLevel = calculateFlightLevel(currentBalance);
        int maxLevel = getMaxFlightLevel();

        if (currentLevel >= maxLevel) {
            playerManager.sendMessage(player, configManager.getMessage("max-level-reached",
                    Map.of("level", String.valueOf(maxLevel))));
            return false;
        }

        double newBalance = currentBalance + amount;
        double maxAmount = getMaxFlightAmount();

        if (newBalance > maxAmount) {
            double maxAmountNeeded = maxAmount - currentBalance;
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

        return economyManager.withdrawMoney(player, amount);
    }

    private void handleLevelUp(Player player, FlightData data, double newBalance) {
        int newLevel = calculateFlightLevel(newBalance);
        if (newLevel > data.getMaxUnlockedLevel()) {
            data.setMaxUnlockedLevel(newLevel);
            playerManager.sendMessage(player, configManager.getMessage("level-up",
                    Map.of("level", String.valueOf(newLevel))));
        }
    }

    private void sendDepositSuccessMessage(Player player, double amount, double newBalance) {
        String currencySymbol = economyManager.getCurrencySymbol();
        int newLevel = calculateFlightLevel(newBalance);

        Map<String, String> placeholders = Map.of(
                "amount", String.valueOf(amount),
                "currency", currencySymbol,
                "balance", String.valueOf(newBalance),
                "level", String.valueOf(newLevel)
        );
        playerManager.sendMessage(player, configManager.getMessage("deposit-success", placeholders));
    }

    private Long getValidPausedTime(UUID playerId) {
        Long pausedTime = pausedFlightTimes.get(playerId);
        if (pausedTime == null || pausedTime <= 0) {
            FlightData data = dataManager.loadPlayerData(playerId);
            pausedTime = data != null ? data.getPausedTime() : 0L;
        }
        return pausedTime > 0 ? pausedTime : null;
    }

    private void startFlightTimer() {
        this.flightTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                processActiveFlights(currentTime);
                processPausedFlights();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void processActiveFlights(long currentTime) {
        Iterator<Map.Entry<UUID, Long>> iterator = activeFlightTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID playerId = entry.getKey();
            Long endTime = entry.getValue();

            if (currentTime >= endTime) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    disableFlight(player);
                    playerManager.sendMessage(player, configManager.getMessage("flight-time-expired"));
                }
                iterator.remove();
                updateFlightData(playerId, false, 0L, null);
            }
        }
    }

    private void processPausedFlights() {
        Iterator<Map.Entry<UUID, Long>> iterator = pausedFlightTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() <= 0) {
                iterator.remove();
                updateFlightData(entry.getKey(), null, null, 0L);
            }
        }
    }

    private void updateFlightData(UUID playerId, Boolean flightActive, Long flightEndTime, Long pausedTime) {
        FlightData data = dataManager.loadPlayerData(playerId);
        if (data != null) {
            if (flightActive != null) data.setFlightActive(flightActive);
            if (flightEndTime != null) data.setFlightEndTime(flightEndTime);
            if (pausedTime != null) data.setPausedTime(pausedTime);
            dataManager.savePlayerData(playerId, data);
        }
    }

    private void startActionBarTimer() {
        this.actionBarTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                activeFlightTimes.keySet().stream()
                        .map(Bukkit::getPlayer)
                        .filter(player -> player != null && player.isOnline())
                        .forEach(player -> actionBarManager.sendFlightTime(player, activeFlightTimes.get(player.getUniqueId())));
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
                if (shouldManageFlight(player) && (!player.getAllowFlight() || !player.isFlying())) {
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

    // Ключевой метод для проверки полёта согласно требованиям
    public boolean shouldManageFlight(Player player) {
        return player.getAllowFlight() || activeFlightTimes.containsKey(player.getUniqueId());
    }

    private boolean isInCreativeOrSpectator(Player player) {
        return player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                player.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    // Геттеры остаются без изменений
    public Long getRemainingFlightTime(Player player) {
        UUID playerId = player.getUniqueId();
        if (activeFlightTimes.containsKey(playerId)) {
            long endTime = activeFlightTimes.get(playerId);
            long remaining = endTime - System.currentTimeMillis();
            return Math.max(remaining, 0);
        }
        return 0L;
    }

    public int calculateFlightLevel(double balance) {
        return configManager.getFlightTiers().values().stream()
                .filter(tier -> balance >= tier.getMinAmount())
                .mapToInt(FlightTier::getLevel)
                .max()
                .orElse(0);
    }

    public double getMaxFlightAmount() {
        return configManager.getFlightTiers().values().stream()
                .mapToDouble(FlightTier::getMinAmount)
                .max()
                .orElse(0);
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
        return pausedFlightTimes.getOrDefault(playerId, 0L);
    }

    public Map<UUID, Long> getPausedFlightTimes() {
        return pausedFlightTimes;
    }

    public void setPausedFlightTime(UUID playerId, long pausedTime) {
        pausedFlightTimes.put(playerId, pausedTime);
    }
}
