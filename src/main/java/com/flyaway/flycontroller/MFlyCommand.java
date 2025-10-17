package com.flyaway.flycontroller;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MFlyCommand implements CommandExecutor, TabCompleter {
    private final FlyPlugin plugin;

    public MFlyCommand(FlyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Эта команда только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("flycontroller.mfly")) {
            player.sendMessage("§cУ вас нет разрешения на использование этой команды!");
            return true;
        }

        if (args.length == 0) {
            showFlightInfo(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                showFlightInfo(player);
                break;

            case "deposit":
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /mfly deposit <сумма>");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    if (amount <= 0) {
                        player.sendMessage("§cСумма должна быть положительной!");
                        return true;
                    }
                    plugin.depositMoney(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cСумма должна быть числом!");
                }
                break;

            case "activate":
                plugin.activateFlight(player);
                break;

            case "continue":
                plugin.continueFlight(player);
                break;

            case "reload":
                if (sender.hasPermission("flycontroller.admin")) {
                    plugin.reloadConfiguration();
                    sender.sendMessage("§aКонфигурация FlyPlugin перезагружена!");
                } else {
                    sender.sendMessage("§cУ вас нет разрешения на эту команду!");
                }
                break;

            default:
                player.sendMessage("§cНеизвестная команда. Используйте:");
                player.sendMessage("§e/mfly info §7- Информация о системе полётов");
                player.sendMessage("§e/mfly deposit <сумма> §7- Внести деньги на счёт");
                player.sendMessage("§e/mfly activate §7- Активировать полёт");
                player.sendMessage("§e/mfly continue §7- Продолжить сохранённый полёт");
                if (sender.hasPermission("flycontroller.admin")) {
                    player.sendMessage("§e/mfly reload §7- Перезагрузить конфигурацию");
                }
                break;
        }

        return true;
    }

    private void showFlightInfo(Player player) {
        FlightData data = plugin.getPlayerFlightData(player);
        String currencySymbol = plugin.getEconomyManager().getCurrencySymbol();

        player.sendMessage("§6=== Система временного полёта ===");
        player.sendMessage("§aВаш баланс: §e" + data.getBalance() + currencySymbol + "§a денег");

        int currentLevel = plugin.calculateFlightLevel(data.getBalance());
        player.sendMessage("§aТекущий уровень: §e" + currentLevel);

        // Показываем прогресс до следующего уровня
        double amountForNextLevel = plugin.getAmountForNextLevel(player);
        if (amountForNextLevel > 0) {
            player.sendMessage("§aДо следующего уровня: §e" + amountForNextLevel + currencySymbol);
        } else if (currentLevel >= plugin.getMaxFlightLevel()) {
            player.sendMessage("§a§lВы достигли максимального уровня! §a🎉");
        }

        player.sendMessage("");
        player.sendMessage("§6Уровни полёта:");

        // Получаем уровни из конфига
        for (FlightTier tier : plugin.getFlightTiers().values()) {
            String status = data.getBalance() >= tier.getMinAmount() ? "§a✓" : "§c✗";

            // Форматируем время - минуты и секунды
            int minutes = tier.getDuration() / 60;
            int seconds = tier.getDuration() % 60;
            String timeString = minutes + " минут" + (seconds > 0 ? " " + seconds + " секунд" : "");

            String levelInfo = "Уровень " + tier.getLevel() + "§7: " +
                timeString + " - §e" + tier.getMinAmount() + currencySymbol;

            // Подсвечиваем текущий уровень
            if (currentLevel == tier.getLevel()) {
                levelInfo = "§e➤ " + levelInfo + " §7(текущий)";
            }

            player.sendMessage(status + " " + levelInfo);
        }

        // Показываем оставшееся время активного полёта
        Long remainingTime = plugin.getRemainingFlightTime(player);
        if (remainingTime > 0) {
            long minutes = remainingTime / 60000;
            long seconds = (remainingTime % 60000) / 1000;
            player.sendMessage("");
            player.sendMessage("§aАктивный полёт: §e" + minutes + " минут " + seconds + " секунд");
        }

        // Показываем сохранённое время
        Long pausedTime = plugin.getPausedFlightTime(player);
        if (pausedTime == null || pausedTime <= 0) {
            pausedTime = data.getPausedTime();
        }

        if (pausedTime != null && pausedTime > 0) {
            long minutes = pausedTime / 60000;
            long seconds = (pausedTime % 60000) / 1000;
            player.sendMessage("");
            player.sendMessage("§bСохранённый полёт: §e" + minutes + " минут " + seconds + " секунд");
            player.sendMessage("§bИспользуйте §e/mfly continue§b для активации");
        }

        // Показываем перезарядку
        if (data.getCooldownEnd() > System.currentTimeMillis()) {
            long remainingCooldown = data.getCooldownEnd() - System.currentTimeMillis();
            long cooldownMinutes = remainingCooldown / 60000;
            long cooldownSeconds = (remainingCooldown % 60000) / 1000;

            if (cooldownMinutes > 0) {
                player.sendMessage("§cПерезарядка: §e" + cooldownMinutes + " минут " + cooldownSeconds + " секунд");
            } else {
                player.sendMessage("§cПерезарядка: §e" + cooldownSeconds + " секунд");
            }
        }

        // Показываем доступные скорости из конфига
        player.sendMessage("");
        player.sendMessage("§6Доступные скорости полёта:");
        StringBuilder speedsInfo = new StringBuilder();
        for (Integer speed : plugin.getFlySpeeds().keySet()) {
            String speedName = getSpeedName(speed);
            speedsInfo.append("§e").append(speed).append("§7 (").append(speedName).append(")§f, ");
        }
        if (speedsInfo.length() > 0) {
            speedsInfo.setLength(speedsInfo.length() - 2); // Убираем последнюю запятую
            player.sendMessage(speedsInfo.toString());
            player.sendMessage("§7Используйте §e/flyspeed <уровень>§7 для изменения скорости");
        }

        player.sendMessage("");
        if (currentLevel < plugin.getMaxFlightLevel()) {
            player.sendMessage("§e/mfly deposit <сумма> §7- Внести деньги на счёт");
        }
        player.sendMessage("§e/mfly activate §7- Активировать полёт");
        if (pausedTime != null && pausedTime > 0) {
            player.sendMessage("§e/mfly continue §7- Продолжить сохранённый полёт");
        }
        if (player.hasPermission("flycontroller.admin")) {
            player.sendMessage("§e/mfly reload §7- Перезагрузить конфигурацию");
        }
    }

    /**
     * Возвращает текстовое название скорости
     */
    private String getSpeedName(int speed) {
        switch (speed) {
            case 1: return "медленно";
            case 2: return "нормально";
            case 3: return "быстро";
            case 4: return "очень быстро";
            default: return "уровень " + speed;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("info");
            completions.add("deposit");
            completions.add("activate");
            completions.add("continue");
            if (sender.hasPermission("flycontroller.admin")) {
                completions.add("reload");
            }
        }

        return completions;
    }
}
