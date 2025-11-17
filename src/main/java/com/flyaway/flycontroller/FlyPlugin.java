package com.flyaway.flycontroller;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class FlyPlugin extends JavaPlugin implements Listener {

    private Map<UUID, Long> activeFlightTimes;
    private Map<UUID, Long> pausedFlightTimes;
    private EconomyManager economyManager;
    private DataManager dataManager;
    private ActionBarManager actionBarManager;
    private ConfigManager configManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Map<Integer, FlightTier> flightTiers;
    private Map<Integer, Float> flySpeeds;
    private List<String> allowedWorlds;
    private long cooldownTime;
    private BukkitTask flightTimerTask;
    private BukkitTask actionBarTimerTask;

    @Override
    public void onEnable() {
        this.activeFlightTimes = new ConcurrentHashMap<>();
        this.pausedFlightTimes = new ConcurrentHashMap<>();

        this.configManager = new ConfigManager(this);
        loadConfigData();
        this.economyManager = new EconomyManager(this);
        loadEconomyManager();
        this.dataManager = new DataManager(this);
        this.actionBarManager = new ActionBarManager(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        MFlyCommand mFlyCommand = new MFlyCommand(this);
        getCommand("mfly").setExecutor(mFlyCommand);
        getCommand("mfly").setTabCompleter(mFlyCommand);
        FlySpeedCommand flySpeedCommand = new FlySpeedCommand(this);
        getCommand("flyspeed").setExecutor(flySpeedCommand);
        getCommand("flyspeed").setTabCompleter(flySpeedCommand);

        // Запуск таймеров
        startFlightTimer();
        startActionBarTimer();

        getLogger().info("Плагин успешно запущен!");
    }

    public void loadConfigData() {
        configManager.loadConfig();

        this.flightTiers = configManager.getFlightTiers();
        this.flySpeeds = configManager.getFlySpeeds();
        this.allowedWorlds = configManager.getWorlds();
        this.cooldownTime = configManager.getCooldownTime();
        String loadedWorlds = allowedWorlds.isEmpty() ? "Все" : String.join(", ", allowedWorlds);
        getLogger().info("Разрешённые миры: " + loadedWorlds);
    }

    public void loadEconomyManager() {
        this.economyManager = new EconomyManager(this);
        getLogger().info("Валюта: " + economyManager.getCurrencyName());
    }

    public void reloadConfiguration() {
        loadConfigData();
        loadEconomyManager();
        getLogger().info("Плагин перезагружен!");

    }

    public Map<Integer, FlightTier> getFlightTiers() {
        return flightTiers;
    }

    public Map<Integer, Float> getFlySpeeds() {
        return flySpeeds;
    }

    public boolean isWorldAllowed(World world) {
        return allowedWorlds.isEmpty() || allowedWorlds.contains(world.getName());
    }

    public void sendMessage(CommandSender sender, String message) {
        if (message.isEmpty()) return;
        String prefix = configManager.getPrefix();
        sender.sendMessage(miniMessage.deserialize(prefix + " " + message));
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public void onDisable() {
        if (flightTimerTask != null) {
            flightTimerTask.cancel();
            flightTimerTask = null;
        }
        if (actionBarTimerTask != null) {
            actionBarTimerTask.cancel();
            actionBarTimerTask = null;
        }

        for (Map.Entry<UUID, Long> entry : activeFlightTimes.entrySet()) {
            UUID playerId = entry.getKey();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                pauseActiveFlight(player, configManager.getMessage("disable-reason.plugin-disable"));
            }
        }
        getLogger().info("Плагин выключен!");
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

            Map<String, String> placeholders = Map.of("reason", reason, "time", formatTime(remainingTime));

            sendMessage(player, configManager.getMessage("flight-paused", placeholders));
        } else {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                sendMessage(player, configManager.getMessage("flight-disabled", Map.of("reason", reason)));
            }
        }

        disableFlight(player);
        activeFlightTimes.remove(playerId);
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
                            sendMessage(player, configManager.getMessage("flight-time-expired"));
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
        }.runTaskTimer(this, 20L, 20L);
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
        }.runTaskTimer(this, 0L, 10L);
    }

    public void handleCombat(Player player) {
        if (player.getAllowFlight() || activeFlightTimes.containsKey(player.getUniqueId())) {
            pauseActiveFlight(player, configManager.getMessage("disable-reason.combat"));
        }
    }

    public boolean continueFlight(Player player) {
        UUID playerId = player.getUniqueId();

        if (!isWorldAllowed(player.getWorld())) {
            sendMessage(player, configManager.getMessage("continue-not-allowed-world"));
            return false;
        }

        Long pausedTimeBeforeActivation = pausedFlightTimes.get(playerId);
        if (pausedTimeBeforeActivation == null) {
            FlightData data = dataManager.loadPlayerData(playerId);
            pausedTimeBeforeActivation = data.getPausedTime();
        }

        if (activatePausedFlight(player)) {
            if (pausedTimeBeforeActivation > 0) {
                Map<String, String> placeholders = Map.of("time", formatTime(pausedTimeBeforeActivation));
                sendMessage(player, configManager.getMessage("flight-continued-time", placeholders));
            } else {
                sendMessage(player, configManager.getMessage("flight-continued"));
            }
            return true;
        } else {
            sendMessage(player, configManager.getMessage("no-saved-flight-time"));
            return false;
        }
    }

    public boolean depositMoney(Player player, double amount) {
        if (!economyManager.isEconomyAvailable()) {
            sendMessage(player, configManager.getMessage("economy-unavailable"));
            return false;
        }

        UUID playerId = player.getUniqueId();
        FlightData data = dataManager.loadPlayerData(playerId);
        double currentBalance = data.getBalance();

        int currentLevel = calculateFlightLevel(currentBalance);
        int maxLevel = getMaxFlightLevel();

        if (currentLevel >= maxLevel) {
            sendMessage(player, configManager.getMessage("max-level-reached",
                    Map.of("level", String.valueOf(maxLevel))));
            return false;
        }

        double newBalance = currentBalance + amount;
        int newLevel = calculateFlightLevel(newBalance);

        if (newLevel > maxLevel) {
            double maxAmountNeeded = getFlightTiers().get(maxLevel).getMinAmount() - currentBalance;
            if (maxAmountNeeded <= 0) {
                sendMessage(player, configManager.getMessage("max-level-reached",
                        Map.of("level", String.valueOf(maxLevel))));
                return false;
            }

            Map<String, String> placeholders = Map.of(
                    "amount", String.valueOf(maxAmountNeeded),
                    "currency", economyManager.getCurrencySymbol()
            );
            sendMessage(player, configManager.getMessage("deposit-max-amount-suggestion", placeholders));
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
            sendMessage(player, configManager.getMessage("level-up",
                    Map.of("level", String.valueOf(newLevel))));
        }

        String currencySymbol = economyManager.getCurrencySymbol();
        Map<String, String> placeholders = Map.of(
                "amount", String.valueOf(amount),
                "currency", currencySymbol,
                "balance", String.valueOf(newBalance),
                "level", String.valueOf(newLevel)
        );
        sendMessage(player, configManager.getMessage("deposit-success", placeholders));

        return true;
    }

    public boolean activateFlight(Player player) {
        UUID playerId = player.getUniqueId();
        FlightData data = dataManager.loadPlayerData(playerId);

        if (data.getBalance() == 0) {
            sendMessage(player, configManager.getMessage("no-balance"));
            return false;
        }

        int maxLevel = calculateFlightLevel(data.getBalance());
        if (maxLevel == 0) {
            sendMessage(player, configManager.getMessage("insufficient-funds"));
            return false;
        }

        if (data.getCooldownEnd() > System.currentTimeMillis()) {
            long remaining = data.getCooldownEnd() - System.currentTimeMillis();
            Map<String, String> placeholders = Map.of("time", formatTime(remaining));
            sendMessage(player, configManager.getMessage("cooldown-active", placeholders));
            return false;
        }

        if (!isWorldAllowed(player.getWorld())) {
            sendMessage(player, configManager.getMessage("activate-not-allowed-world"));
            return false;
        }

        FlightTier tier = flightTiers.get(maxLevel);
        long endTime = System.currentTimeMillis() + (tier.getDuration() * 1000L);
        activeFlightTimes.put(playerId, endTime);

        data.setFlightActive(true);
        data.setFlightEndTime(endTime);
        data.setCooldownEnd(endTime + cooldownTime);

        dataManager.savePlayerData(playerId, data);

        enableFlight(player);

        Map<String, String> placeholders = Map.of(
                "level", String.valueOf(maxLevel),
                "minutes", String.valueOf(tier.getDuration() / 60)
        );
        sendMessage(player, configManager.getMessage("flight-activated", placeholders));

        return true;
    }

    public Long getPausedFlightTime(Player player) {
        return pausedFlightTimes.get(player.getUniqueId());
    }

    public String formatTime(long milliseconds) {
        if (milliseconds <= 0) return "0";
        long minutes = milliseconds / 60000;
        long seconds = (milliseconds % 60000) / 1000;

        Map<String, String> timePlaceholders = Map.of(
                "minutes", String.valueOf(minutes),
                "seconds", String.valueOf(seconds)
        );
        return configManager.getMessage("time-format", timePlaceholders);
    }

    public Long setPausedFlightTime(UUID playerId, long pausedTime) {
        return pausedFlightTimes.put(playerId, pausedTime);
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    private void enableFlight(Player player) {
        // Включаем полёт с задержкой, чтобы Essentials успел завершить свою логику
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }

            if (!player.isFlying()) {
                player.setFlying(true);
            }

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (activeFlightTimes.containsKey(player.getUniqueId()) && (!player.getAllowFlight() || !player.isFlying())) {
                    player.setAllowFlight(true);
                    player.setFlying(true);
                }
            }, 10L);
        }, 20L);
    }

    public void disableFlight(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
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

    public Map<UUID, Long> getActiveFlightTimes() {
        return activeFlightTimes;
    }

    public int getMaxFlightLevel() {
        return flightTiers.keySet().stream()
                .max(Integer::compareTo)
                .orElse(0);
    }

    public int calculateFlightLevel(double balance) {
        int highestLevel = 0;
        for (FlightTier tier : flightTiers.values()) {
            if (balance >= tier.getMinAmount()) {
                highestLevel = Math.max(highestLevel, tier.getLevel());
            }
        }
        return highestLevel;
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

        getLogger().info("Активирован полёт из паузы для " + player.getName() + ", время: " + (pausedTime / 1000) + " сек");

        return true;
    }
}
