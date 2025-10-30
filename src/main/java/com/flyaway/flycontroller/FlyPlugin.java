package com.flyaway.flycontroller;

import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

public class FlyPlugin extends JavaPlugin implements Listener {

    private Map<UUID, FlightData> playerFlightData;
    private Map<UUID, Long> activeFlightTimes;
    private EconomyManager economyManager;
    private DataManager dataManager;
    private ActionBarManager actionBarManager;
    private ConfigManager configManager;
    private Map<UUID, Long> pausedFlightTimes = new HashMap<>();

    private Map<Integer, FlightTier> flightTiers;
    private Map<Integer, Float> flySpeeds;
    private List<String> allowedWorlds;
    private long cooldownTime;
    private BukkitTask flightTimerTask;
    private BukkitTask actionBarTimerTask;

    @Override
    public void onEnable() {
        this.playerFlightData = new HashMap<>();
        this.activeFlightTimes = new HashMap<>();
        this.pausedFlightTimes = new HashMap<>();

        // Инициализация менеджеров
        this.configManager = new ConfigManager(this);
        configManager.loadConfig();

        // Загрузка настроек из конфига
        this.flightTiers = configManager.getFlightTiers();
        this.flySpeeds = configManager.getFlySpeeds();
        this.allowedWorlds = configManager.getWorlds();
        this.cooldownTime = configManager.getCooldownTime();

        this.economyManager = new EconomyManager(this);
        this.dataManager = new DataManager(this);
        this.actionBarManager = new ActionBarManager(this);

        // Загрузка данных
        dataManager.loadAllPlayerData();

        // Регистрация событий
        getServer().getPluginManager().registerEvents(this, this);

        // Регистрация команд
        getCommand("mfly").setExecutor(new MFlyCommand(this));
        getCommand("flyspeed").setExecutor(new FlySpeedCommand(this));

        // Запуск таймеров
        startFlightTimer();
        startActionBarTimer();

        getLogger().info("Плагин успешно запущен!");
        getLogger().info("Разрешённые миры: " + String.join(", ", allowedWorlds));
        getLogger().info("Валюта: " + economyManager.getCurrencyName());
    }

    public void reloadConfiguration() {
        configManager.loadConfig();

        // Обновляем настройки
        this.flightTiers = configManager.getFlightTiers();
        this.flySpeeds = configManager.getFlySpeeds();
        this.allowedWorlds = configManager.getWorlds();
        this.cooldownTime = configManager.getCooldownTime();

        // Переинициализируем EconomyManager для обновления валюты
        this.economyManager = new EconomyManager(this);

        getLogger().info("Конфигурация перезагружена!");
        getLogger().info("Разрешённые миры: " + String.join(", ", allowedWorlds));
        getLogger().info("Валюта: " + economyManager.getCurrencyName());
    }

    public Map<Integer, FlightTier> getFlightTiers() {
        return flightTiers;
    }

    public Map<Integer, Float> getFlySpeeds() {
        return flySpeeds;
    }

    public boolean isWorldAllowed(World world) {
        return allowedWorlds.contains(world.getName());
    }

    // Добавьте геттер для списка миров
    public List<String> getAllowedWorlds() {
        return allowedWorlds;
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
        // Сохраняем данные всех игроков
        dataManager.saveAllPlayerData();

        // Отключаем полёт всем игрокам при выключении плагина
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getAllowFlight() && activeFlightTimes.containsKey(player.getUniqueId())) {
                disableFlight(player);
            }
        }
        getLogger().info("Плагин выключен!");
    }

    private void startFlightTimer() {
        this.flightTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                // Проверяем активные полёты
                Iterator<Map.Entry<UUID, Long>> activeIterator = activeFlightTimes.entrySet().iterator();
                while (activeIterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = activeIterator.next();
                    UUID playerId = entry.getKey();
                    Long endTime = entry.getValue();

                    if (currentTime >= endTime) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            disableFlight(player);
                            player.sendMessage("§cВремя вашего полёта истекло!");
                        }
                        activeIterator.remove();

                        // ОЧИЩАЕМ данные игрока
                        FlightData data = playerFlightData.get(playerId);
                        if (data != null) {
                            data.setFlightActive(false);
                            data.setFlightEndTime(0);
                            dataManager.savePlayerData(playerId);
                        }
                    }
                }

                // Проверяем сохранённые полёты (удаляем если истекли)
                Iterator<Map.Entry<UUID, Long>> pausedIterator = pausedFlightTimes.entrySet().iterator();
                while (pausedIterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = pausedIterator.next();
                    // Удаляем если сохранённое время истекло
                    if (entry.getValue() <= 0) {
                        pausedIterator.remove();

                        // ОЧИЩАЕМ данные игрока
                        FlightData data = playerFlightData.get(entry.getKey());
                        if (data != null) {
                            data.setPausedTime(0);
                            dataManager.savePlayerData(entry.getKey());
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
                        // Отправляем ActionBar независимо от состояния полёта
                        actionBarManager.sendFlightTime(player, entry.getValue());
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!isWorldAllowed(world)) {
            if (player.getAllowFlight() || activeFlightTimes.containsKey(player.getUniqueId())) {
                UUID playerId = player.getUniqueId();

                // Сохраняем оставшееся время
                Long remainingTime = getRemainingFlightTime(player);
                if (remainingTime > 0) {
                    // Сохраняем в данные игрока
                    FlightData data = getPlayerFlightData(playerId);
                    data.setPausedTime(remainingTime);
                    data.setFlightActive(false);

                    // Сохраняем для текущей сессии
                    pausedFlightTimes.put(playerId, remainingTime);

                    // Сохраняем в файл
                    dataManager.savePlayerData(playerId);

                    player.sendMessage("§cПолёт отключён! Вы находитесь не в разрешённом мире.");
                    player.sendMessage("§eОставшееся время сохранено. Используйте §6/mfly continue§e в разрешённом мире для продолжения.");
                }
                disableFlight(player);
                activeFlightTimes.remove(playerId);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Если у игрока активный полёт - ставим на паузу
        if (activeFlightTimes.containsKey(playerId)) {
            Long remainingTime = getRemainingFlightTime(player);
            if (remainingTime > 0) {
                // Сохраняем оставшееся время
                FlightData data = getPlayerFlightData(playerId);
                data.setPausedTime(remainingTime);
                data.setFlightActive(false);
                data.setFlightEndTime(0);

                // Сохраняем в файл
                dataManager.savePlayerData(playerId);
            }

            // Отключаем полёт и удаляем из активных
            disableFlight(player);
            activeFlightTimes.remove(playerId);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        World world = player.getWorld();

        // Загружаем данные игрока
        dataManager.loadPlayerData(playerId);

        FlightData data = playerFlightData.get(playerId);

        // ВОССТАНАВЛИВАЕМ СОХРАНЁННЫЙ ПОЛЁТ
        if (data != null && data.getPausedTime() > 0) {
            // СОХРАНЯЕМ время ДО вызова activatePausedFlight
            long savedPausedTime = data.getPausedTime();

            // Переносим сохранённое время в pausedFlightTimes
            pausedFlightTimes.put(playerId, savedPausedTime);
            // АВТОМАТИЧЕСКИ активируем полёт если игрок в разрешённом мире
            if (isWorldAllowed(world)) {
                if (activatePausedFlight(player)) {
                    // Используем СОХРАНЁННОЕ время для сообщения
                    long minutes = savedPausedTime / 60000;
                    long seconds = (savedPausedTime % 60000) / 1000;
                    player.sendMessage("§aСохранённый полёт автоматически восстановлен! Оставшееся время: §e" + minutes + " минут " + seconds + " секунд");
                } else {
                    player.sendMessage("§cНе удалось автоматически активировать сохранённый полёт. Используйте §e/mfly continue");
                }
            } else {
                // Игрок не в основном мире
                long minutes = savedPausedTime / 60000;
                long seconds = (savedPausedTime % 60000) / 1000;
                player.sendMessage("§aУ вас есть сохранённое время полёта: §e" + minutes + " минут " + seconds + " секунд");
                player.sendMessage("§aКогда вернётесь в разрешённый мир, используйте §e/mfly continue§a для активации");
            }
        }

        // Отключаем полёт, если игрок не в разрешённом мире
        if (!isWorldAllowed(world)) {
            disableFlight(player);
        }
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        // Проверяем, что обе стороны - игроки
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            // Вызываем handleCombat только для игроков
            handleCombat(attacker);
            handleCombat(victim);
        }
    }

    private void handleCombat(Player player) {
        if (player.getAllowFlight()) {
            UUID playerId = player.getUniqueId();

            if (activeFlightTimes.containsKey(playerId) && player.getGameMode() != GameMode.CREATIVE) {
                // Сохраняем оставшееся время
                Long remainingTime = getRemainingFlightTime(player);
                if (remainingTime > 0) {
                    // Сохраняем в данные игрока (для перезагрузок)
                    FlightData data = getPlayerFlightData(playerId);
                    data.setPausedTime(remainingTime);
                    data.setFlightActive(false);

                    // Сохраняем для текущей сессии
                    pausedFlightTimes.put(playerId, remainingTime);

                    // Сохраняем в файл
                    dataManager.savePlayerData(playerId);

                    player.sendMessage("§cПолёт отключён из-за вступления в бой!");
                    player.sendMessage("§eОставшееся время сохранено. Используйте §6/mfly continue§e для продолжения полёта.");
                } else {
                    player.sendMessage("§cПолёт отключён из-за вступления в бой!");
                }
                activeFlightTimes.remove(playerId);
            }

            disableFlight(player);
        }
    }

    public boolean continueFlight(Player player) {
        UUID playerId = player.getUniqueId();

        // Проверка мира
        if (!isWorldAllowed(player.getWorld())) {
            player.sendMessage("§cВы можете продолжить полёт только в разрешённом мире!");
            return false;
        }

        // СОХРАНЯЕМ время ДО активации
        Long pausedTimeBeforeActivation = pausedFlightTimes.get(playerId);
        if (pausedTimeBeforeActivation == null) {
            // Если не в сессии, то из данных
            FlightData data = getPlayerFlightData(playerId);
            pausedTimeBeforeActivation = data.getPausedTime();
        }

        // Используем внутренний метод активации
        if (activatePausedFlight(player)) {
            if (pausedTimeBeforeActivation != null && pausedTimeBeforeActivation > 0) {
                long minutes = pausedTimeBeforeActivation / 60000;
                long seconds = (pausedTimeBeforeActivation % 60000) / 1000;
                player.sendMessage("§aПолёт продолжен! Оставшееся время: §e" + minutes + " минут " + seconds + " секунд");
            } else {
                player.sendMessage("§aПолёт продолжен!");
            }
            return true;
        } else {
            player.sendMessage("§cУ вас нет сохранённого времени полёта!");
            return false;
        }
    }

    public boolean depositMoney(Player player, double amount) {
        // Проверка мира
        if (!isWorldAllowed(player.getWorld())) {
            player.sendMessage("§cВы можете вносить деньги на счёт полётов только в разрешённом мире!");
            return false;
        }

        // Проверка доступности экономики
        if (!economyManager.isEconomyAvailable()) {
            player.sendMessage("§cСистема экономики недоступна! Обратитесь к администратору.");
            return false;
        }

        // Получаем текущие данные игрока
        UUID playerId = player.getUniqueId();
        FlightData data = getPlayerFlightData(playerId);
        double currentBalance = data.getBalance();

        // Проверяем, достигнут ли максимальный уровень
        int currentLevel = calculateFlightLevel(currentBalance);
        int maxLevel = getMaxFlightLevel();

        if (currentLevel >= maxLevel) {
            player.sendMessage("§aВы уже достигли максимального уровня полёта (" + maxLevel + ")!");
            player.sendMessage("§aДальнейшее пополнение счёта не требуется.");
            return false;
        }

        // Проверяем, не превысит ли новая сумма максимальный уровень
        double newBalance = currentBalance + amount;
        int newLevel = calculateFlightLevel(newBalance);

        // Если после пополнения превысим максимальный уровень, корректируем сумму
        if (newLevel > maxLevel) {
            double maxAmountNeeded = getFlightTiers().get(maxLevel).getMinAmount() - currentBalance;
            if (maxAmountNeeded <= 0) {
                player.sendMessage("§aВы уже достигли максимального уровня полёта!");
                return false;
            }

            // Предлагаем внести только нужную сумму до максимального уровня
            player.sendMessage("§eДля достижения максимального уровня вам нужно внести только §6" + maxAmountNeeded + economyManager.getCurrencySymbol());
            player.sendMessage("§eХотите внести именно эту сумму? Используйте: §6/mfly deposit " + maxAmountNeeded);
            return false;
        }

        // Проверка денег
        if (!economyManager.hasEnoughMoney(player, amount)) {
            return false;
        }

        // Списываем деньги
        if (!economyManager.withdrawMoney(player, amount)) {
            return false;
        }

        // Добавляем деньги на счёт полётов
        data.setBalance(newBalance);
        playerFlightData.put(playerId, data);

        // Проверяем, достигнут ли новый уровень
        if (newLevel > data.getMaxUnlockedLevel()) {
            data.setMaxUnlockedLevel(newLevel);
            player.sendMessage("§aПоздравляем! Вы достигли уровня полёта " + newLevel + "!");
        }

        // Сохраняем данные
        dataManager.savePlayerData(playerId);

        String currencySymbol = economyManager.getCurrencySymbol();
        player.sendMessage("§aВы внесли §e" + amount + currencySymbol + "§a денег на счёт полётов.");
        player.sendMessage("§aТекущий баланс: §e" + newBalance + currencySymbol + "§a денег");
        player.sendMessage("§aТекущий уровень полёта: §e" + newLevel);

        return true;
    }

    public boolean activateFlight(Player player) {
        UUID playerId = player.getUniqueId();
        FlightData data = getPlayerFlightData(playerId);

        if (data.getBalance() == 0) {
            player.sendMessage("§cУ вас нет денег на счёте полётов! Используйте /mfly deposit <сумма>");
            return false;
        }

        // Определяем максимальный доступный уровень
        int maxLevel = calculateFlightLevel(data.getBalance());
        if (maxLevel == 0) {
            player.sendMessage("§cУ вас недостаточно средств для активации полёта!");
            return false;
        }

        // Проверка перезарядки
        if (data.getCooldownEnd() > System.currentTimeMillis()) {
            long remaining = data.getCooldownEnd() - System.currentTimeMillis();
            long minutes = remaining / 60000;
            long seconds = (remaining % 60000) / 1000;
            player.sendMessage("§cВы можете активировать полёт снова через " + minutes + " минут " + seconds + " секунд");
            return false;
        }

        // Проверка мира
        if (!isWorldAllowed(player.getWorld())) {
            player.sendMessage("§cВы можете активировать полёт только в разрешённом мире!");
            return false;
        }

        // Активируем полёт максимального уровня
        FlightTier tier = flightTiers.get(maxLevel);
        long endTime = System.currentTimeMillis() + (tier.getDuration() * 1000);
        activeFlightTimes.put(playerId, endTime);

        // Обновляем данные игрока
        data.setFlightActive(true);
        data.setFlightEndTime(endTime);
        data.setCooldownEnd(endTime + cooldownTime);

        // Сохраняем данные
        dataManager.savePlayerData(playerId);

        enableFlight(player);

        player.sendMessage("§aПолёт уровня " + maxLevel + " активирован на " +
                          tier.getDuration() / 60 + " минут!");

        return true;
    }

    public Long getPausedFlightTime(Player player) {
        return pausedFlightTimes.get(player.getUniqueId());
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

            // Двойная проверка через ещё одну задержку
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (activeFlightTimes.containsKey(player.getUniqueId()) && (!player.getAllowFlight() || !player.isFlying())) {
                    player.setAllowFlight(true);
                    player.setFlying(true);
                }
            }, 10L); // Ещё через 0.5 секунды
        }, 20L); // Задержка 1 секунда после входа
    }

    private void disableFlight(Player player) {
        // НЕ отключаем полёт если игрок в креативе
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    public FlightData getPlayerFlightData(Player player) {
        return getPlayerFlightData(player.getUniqueId());
    }

    public FlightData getPlayerFlightData(UUID playerId) {
        return playerFlightData.getOrDefault(playerId, new FlightData());
    }

    public void setPlayerFlightData(UUID playerId, FlightData data) {
        playerFlightData.put(playerId, data);
    }

    public Map<UUID, FlightData> getAllPlayerFlightData() {
        return playerFlightData;
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

    // Получить максимальный доступный уровень полёта
    public int getMaxFlightLevel() {
        return flightTiers.keySet().stream()
                .max(Integer::compareTo)
                .orElse(0);
    }

    // Пересчитать уровень на основе баланса (исправленная версия)
    public int calculateFlightLevel(double balance) {
        int highestLevel = 0;
        for (FlightTier tier : flightTiers.values()) {
            if (balance >= tier.getMinAmount()) {
                highestLevel = Math.max(highestLevel, tier.getLevel());
            }
        }
        return highestLevel;
    }

    // Получить сумму, необходимую для следующего уровня
    public double getAmountForNextLevel(Player player) {
        FlightData data = getPlayerFlightData(player);
        double currentBalance = data.getBalance();
        int currentLevel = calculateFlightLevel(currentBalance);
        int nextLevel = currentLevel + 1;

        FlightTier nextTier = flightTiers.get(nextLevel);
        if (nextTier == null) {
            return 0; // Максимальный уровень уже достигнут
        }

        return Math.max(0, nextTier.getMinAmount() - currentBalance);
    }

    private boolean activatePausedFlight(Player player) {
        UUID playerId = player.getUniqueId();

        // Получаем данные игрока
        FlightData data = getPlayerFlightData(playerId);
        if (data == null) {
            return false;
        }

        // ПРОВЕРЯЕМ СНАЧАЛА В СЕССИИ, ПОТОМ В ДАННЫХ
        Long pausedTime = pausedFlightTimes.get(playerId);
        if (pausedTime == null || pausedTime <= 0) {
            pausedTime = data.getPausedTime();
            if (pausedTime <= 0) {
                return false;
            }
            // Если нашли в данных, добавляем в сессию
            pausedFlightTimes.put(playerId, pausedTime);
        }

        // Проверяем, не активирован ли уже полёт
        if (activeFlightTimes.containsKey(playerId)) {
            return false;
        }

        // Активируем полёт с оставшимся временем
        long endTime = System.currentTimeMillis() + pausedTime;
        activeFlightTimes.put(playerId, endTime);

        // Обновляем данные игрока
        data.setFlightActive(true);
        data.setFlightEndTime(endTime);
        data.setPausedTime(0); // Очищаем сохранённое время

        // Сохраняем данные
        dataManager.savePlayerData(playerId);

        // Удаляем из паузы сессии
        pausedFlightTimes.remove(playerId);

        // ВКЛЮЧАЕМ полёт
        enableFlight(player);

        getLogger().info("Активирован полёт из паузы для " + player.getName() +
            ", время: " + (pausedTime/1000) + " сек");

        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase().trim();

        // Убираем слэш в начале если есть
        if (message.startsWith("/")) {
            message = message.substring(1);
        }

        String[] args = message.split(" ");
        String command = args[0];

        // Обрабатываем команду fly (с любыми алиасами)
        if (command.equals("fly") || command.equals("essentials:fly")) {
            if (!isWorldAllowed(player.getWorld())) {
                player.sendMessage("§cКоманда /fly доступна только в разрешённом мире!");
                event.setCancelled(true);
                return;
            }
        }
    }
}
